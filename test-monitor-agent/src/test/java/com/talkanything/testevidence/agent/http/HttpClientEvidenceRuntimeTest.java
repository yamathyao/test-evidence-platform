package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpClientEvidenceRuntimeTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void reportsClientEvidenceWithChildSpan() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> json = new AtomicReference<String>();
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1,
                (events, diagnostics) -> capture(events, json, delivered));
        HttpClientEvidenceRuntime.initialize("order-service", reporter);
        TestContext parent = TestContextHolder.enter("run-1", "case-1", "profile-1", 1, "trace-1", "", false);

        HttpClientEvidenceRuntime.ClientState state = HttpClientEvidenceRuntime.begin("GET", "http://inventory/items");
        HttpClientEvidenceRuntime.complete(state, Integer.valueOf(201), null);

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertTrue(json.get().contains("\"protocol\":\"HTTP\""));
        assertTrue(json.get().contains("\"direction\":\"CLIENT\""));
        assertTrue(json.get().contains("\"target\":\"http://inventory/items\""));
        assertTrue(json.get().contains("\"statusCode\":201"));
        assertTrue(json.get().contains("\"parentSpanId\":\"" + parent.spanId() + "\""));
        assertFalse(json.get().contains("\"httpPayload\""));
        reporter.close();
    }

    @Test
    void doesNothingWithoutActiveContext() {
        assertNull(HttpClientEvidenceRuntime.begin("GET", "http://inventory/items"));
    }

    private static void capture(List<EvidencePayload> events, AtomicReference<String> json, CountDownLatch delivered) {
        for (EvidencePayload event : events) {
            String value = event.toJson();
            if (value.contains("\"protocol\":\"HTTP\"") && value.contains("\"direction\":\"CLIENT\"")) {
                json.set(value);
                delivered.countDown();
            }
        }
    }
}