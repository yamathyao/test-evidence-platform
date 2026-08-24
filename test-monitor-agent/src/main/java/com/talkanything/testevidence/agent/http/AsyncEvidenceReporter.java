package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class AsyncEvidenceReporter implements AutoCloseable {
    public interface BatchSink { void send(List<EvidencePayload> events) throws Exception; }
    private final ArrayBlockingQueue<EvidencePayload> queue;
    private final int batchSize;
    private final BatchSink sink;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong dropped = new AtomicLong();

    public AsyncEvidenceReporter(int queueCapacity, int batchSize, BatchSink sink) {
        this.queue = new ArrayBlockingQueue<EvidencePayload>(queueCapacity);
        this.batchSize = batchSize;
        this.sink = sink;
        Thread worker = new Thread(this::drain, "test-evidence-reporter");
        worker.setDaemon(true);
        worker.start();
    }
    public void report(EvidencePayload event) { if (!closed.get() && !queue.offer(event)) dropped.incrementAndGet(); }
    public long droppedCount() { return dropped.get(); }
    public boolean isClosed() { return closed.get(); }
    public void close() { closed.set(true); }
    public static BatchSink httpSink(final String collectorUrl, final String token) {
        return new BatchSink() {
            @Override public void send(List<EvidencePayload> events) throws Exception {
                HttpURLConnection connection = (HttpURLConnection) new URL(collectorUrl + "/internal/v1/evidence/batches").openConnection();
                connection.setConnectTimeout(1000); connection.setReadTimeout(1000); connection.setRequestMethod("POST");
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-Test-Agent-Token", token);
                byte[] bytes = batchJson(events).getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream output = connection.getOutputStream();
                try { output.write(bytes); } finally { output.close(); }
                int status = connection.getResponseCode();
                AgentDiagnostics.log("evidence.delivery.endpoint.port=" + connection.getURL().getPort()
                        + " path=" + connection.getURL().getPath());
                AgentDiagnostics.log("evidence.delivery.status=" + status + " count=" + events.size());
                if (status >= 300) throw new IllegalStateException("Evidence endpoint rejected batch");
            }
        };
    }

    private void drain() {
        while (!closed.get()) {
            try {
                EvidencePayload first = queue.take();
                List<EvidencePayload> batch = new ArrayList<EvidencePayload>();
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                try { sink.send(batch); }
                catch (Exception exception) {
                    AgentDiagnostics.log("evidence.delivery.failure=" + exception.getClass().getSimpleName() + " count=" + batch.size());
                    dropped.addAndGet(batch.size());
                }
            } catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
            catch (Throwable ignored) { dropped.incrementAndGet(); }
        }
    }
    private static String batchJson(List<EvidencePayload> events) {
        StringBuilder builder = new StringBuilder("{\"events\":[");
        for (int index = 0; index < events.size(); index++) { if (index > 0) builder.append(','); builder.append(events.get(index).toJson()); }
        return builder.append("]}").toString();
    }
}
