package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncEvidenceReporter implements AutoCloseable {
    public interface BatchSink { void send(List<EvidencePayload> events, List<DeliveryDiagnostic> diagnostics) throws Exception; }
    private final ArrayBlockingQueue<EvidencePayload> queue;
    private final int batchSize;
    private final BatchSink sink;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong dropped = new AtomicLong();
    private final ConcurrentMap<String, DiagnosticCounter> diagnostics = new ConcurrentHashMap<String, DiagnosticCounter>();

    public AsyncEvidenceReporter(int queueCapacity, int batchSize, BatchSink sink) {
        this.queue = new ArrayBlockingQueue<EvidencePayload>(queueCapacity);
        this.batchSize = batchSize; this.sink = sink;
        Thread worker = new Thread(this::drain, "test-evidence-reporter");
        worker.setDaemon(true); worker.start();
    }

    public void report(EvidencePayload event) {
        if (!closed.get() && !queue.offer(event)) recordDropped(event);
    }
    public long droppedCount() { return dropped.get(); }
    public boolean isClosed() { return closed.get(); }
    public void close() { closed.set(true); }

    public static BatchSink httpSink(final String collectorUrl, final String token) {
        return new BatchSink() {
            @Override public void send(List<EvidencePayload> events, List<DeliveryDiagnostic> diagnostics) throws Exception {
                HttpURLConnection connection = (HttpURLConnection) new URL(collectorUrl + "/internal/v1/evidence/batches").openConnection();
                connection.setConnectTimeout(1000); connection.setReadTimeout(1000); connection.setRequestMethod("POST");
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-Test-Agent-Token", token);
                byte[] bytes = batchJson(events, diagnostics).getBytes(StandardCharsets.UTF_8);
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
                batch.add(first); queue.drainTo(batch, batchSize - 1);
                try { sink.send(batch, diagnosticSnapshot()); }
                catch (Throwable exception) { recordFailedBatch(batch, exception); }
            } catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
            catch (Throwable ignored) { dropped.incrementAndGet(); }
        }
    }

    private void recordDropped(EvidencePayload event) {
        dropped.incrementAndGet();
        counter(event).recordDrop();
    }
    private void recordFailedBatch(List<EvidencePayload> events, Throwable exception) {
        Set<String> failedServices = new HashSet<String>();
        for (EvidencePayload event : events) {
            recordDropped(event);
            String key = key(event);
            if (failedServices.add(key)) counter(event).recordFailure(exception.getClass().getSimpleName());
        }
        AgentDiagnostics.log("evidence.delivery.failure=" + exception.getClass().getSimpleName() + " count=" + events.size());
    }
    private DiagnosticCounter counter(EvidencePayload event) {
        String key = key(event);
        DiagnosticCounter existing = diagnostics.get(key);
        if (existing != null) return existing;
        DiagnosticCounter created = new DiagnosticCounter(event);
        DiagnosticCounter previous = diagnostics.putIfAbsent(key, created);
        return previous == null ? created : previous;
    }
    private String key(EvidencePayload event) { return event.runId() + "\u0000" + event.serviceName(); }
    private List<DeliveryDiagnostic> diagnosticSnapshot() {
        List<DeliveryDiagnostic> values = new ArrayList<DeliveryDiagnostic>();
        for (DiagnosticCounter counter : diagnostics.values()) values.add(counter.snapshot());
        Collections.sort(values, new Comparator<DeliveryDiagnostic>() {
            @Override public int compare(DeliveryDiagnostic left, DeliveryDiagnostic right) {
                return (left.runId() + left.serviceName()).compareTo(right.runId() + right.serviceName());
            }
        });
        return values;
    }
    private static String batchJson(List<EvidencePayload> events, List<DeliveryDiagnostic> diagnostics) {
        StringBuilder builder = new StringBuilder("{\"events\":[");
        for (int index = 0; index < events.size(); index++) { if (index > 0) builder.append(','); builder.append(events.get(index).toJson()); }
        builder.append("],\"diagnostics\":[");
        for (int index = 0; index < diagnostics.size(); index++) { if (index > 0) builder.append(','); builder.append(diagnostics.get(index).toJson()); }
        return builder.append("]}").toString();
    }

    private static final class DiagnosticCounter {
        private final String runId; private final String profileId; private final int profileVersion; private final String serviceName;
        private final AtomicLong droppedEvidenceCount = new AtomicLong();
        private final AtomicLong deliveryFailureCount = new AtomicLong();
        private volatile String lastFailure = "";
        DiagnosticCounter(EvidencePayload event) {
            this.runId = event.runId(); this.profileId = event.profileId(); this.profileVersion = event.profileVersion();
            this.serviceName = event.serviceName();
        }
        void recordDrop() { droppedEvidenceCount.incrementAndGet(); }
        void recordFailure(String failure) { deliveryFailureCount.incrementAndGet(); lastFailure = failure; }
        DeliveryDiagnostic snapshot() {
            return new DeliveryDiagnostic(runId, profileId, profileVersion, serviceName, droppedEvidenceCount.get(),
                    deliveryFailureCount.get(), lastFailure);
        }
    }
}
