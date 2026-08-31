package com.talkanything.testevidence.agent.dubbo;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import com.talkanything.testevidence.agent.http.AsyncEvidenceReporter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DubboEvidenceRuntimeTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void reportsClientServerPairAndRestoresProviderContext() throws Exception {
        CountDownLatch delivered = new CountDownLatch(2);
        List<EvidencePayload> captured = new CopyOnWriteArrayList<EvidencePayload>();
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1, (events, diagnostics) -> {
            captured.addAll(events);
            for (EvidencePayload ignored : events) delivered.countDown();
        });
        DubboEvidenceRuntime.initialize("order-service", reporter);
        TestContext parent = TestContextHolder.enter("run", "case", "profile", 1);
        Map<String, String> attachments = new HashMap<String, String>();

        DubboEvidenceRuntime.ConsumerState client = DubboEvidenceRuntime.enterConsumer(
                attachments, "com.example.EchoService#echo");
        DubboEvidenceRuntime.ProviderState server = DubboEvidenceRuntime.enterProvider(
                attachments, "com.example.EchoService#echo");

        assertNotNull(client);
        assertNotNull(server);
        assertEquals(server.context().spanId(), TestContextHolder.current().get().spanId());

        DubboEvidenceRuntime.completeProvider(server, null);
        DubboEvidenceRuntime.completeConsumer(client, null);

        assertEquals(parent.spanId(), TestContextHolder.current().get().spanId());
        assertTrue(delivered.await(1, TimeUnit.SECONDS));
        List<String> json = toJson(captured);
        assertTrue(hasDirection(json, "CLIENT", parent.spanId()));
        assertTrue(hasDirection(json, "SERVER", client.context().spanId()));
        assertFalse(json.toString().contains("order-secret"));
        reporter.close();
    }

    @Test
    void capturesDubboArgumentsAndResultWhenPayloadCaptureIsEnabled() throws Exception {
        CountDownLatch delivered = new CountDownLatch(2);
        List<EvidencePayload> captured = new CopyOnWriteArrayList<EvidencePayload>();
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1, (events, diagnostics) -> {
            captured.addAll(events);
            for (EvidencePayload ignored : events) delivered.countDown();
        });
        DubboEvidenceRuntime.initialize("order-service", reporter);
        TestContextHolder.enter("run", "case", "profile", 1, true);
        Map<String, String> attachments = new HashMap<String, String>();

        DubboEvidenceRuntime.ConsumerState client = DubboEvidenceRuntime.enterConsumer(
                attachments, "com.example.EchoService#echo", new Object[] { "ORD-1" });
        DubboEvidenceRuntime.ProviderState server = DubboEvidenceRuntime.enterProvider(
                attachments, "com.example.EchoService#echo", new Object[] { "ORD-1" });
        DubboEvidenceRuntime.completeProvider(server, new Result(new Reply("ORD-1")), null);
        DubboEvidenceRuntime.completeConsumer(client, new Result(new Reply("ORD-1")), null);

        assertEquals("true", attachments.get("x-test-capture-protocol-payload"));
        assertTrue(delivered.await(1, TimeUnit.SECONDS));
        for (String json : toJson(captured)) {
            assertTrue(json.contains("\"protocolPayload\""));
            assertTrue(json.contains("\"requestBody\":\"[\\\"ORD-1\\\"]\""));
            assertTrue(json.contains("\"responseBody\":\"{\\\"orderNo\\\":\\\"ORD-1\\\"}\""));
        }
        reporter.close();
    }

    private static List<String> toJson(List<EvidencePayload> events) {
        List<String> json = new ArrayList<String>();
        for (EvidencePayload event : events) json.add(event.toJson());
        return json;
    }

    private static boolean hasDirection(List<String> json, String direction, String parentSpanId) {
        for (String event : json) {
            if (event.contains("\"protocol\":\"DUBBO\"") && event.contains("\"direction\":\"" + direction)
                    && event.contains("\"parentSpanId\":\"" + parentSpanId)) return true;
        }
        return false;
    }

    public static final class Result {
        private final Object value;
        public Result(Object value) { this.value = value; }
        public Object getValue() { return value; }
    }

    public static final class Reply {
        private final String orderNo;
        public Reply(String orderNo) { this.orderNo = orderNo; }
        public String getOrderNo() { return orderNo; }
    }
}
