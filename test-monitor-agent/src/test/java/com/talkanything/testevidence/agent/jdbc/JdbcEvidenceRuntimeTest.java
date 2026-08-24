package com.talkanything.testevidence.agent.jdbc;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import com.talkanything.testevidence.agent.http.AsyncEvidenceReporter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcEvidenceRuntimeTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void reportsJdbcChildSpanWithoutRawParameterValue() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<List<EvidencePayload>> captured = new AtomicReference<List<EvidencePayload>>();
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1, events -> {
            captured.set(events);
            delivered.countDown();
        });
        JdbcEvidenceRuntime.initialize("order-service", reporter);
        TestContext parent = TestContextHolder.enter("run", "case", "profile", 1);
        Object statement = new Object();

        JdbcEvidenceRuntime.recordParameter(statement, "setString", 1, "PAID");
        JdbcEvidenceRuntime.complete(statement, "UPDATE orders SET status = ?", "UPDATE", System.currentTimeMillis(), null);

        assertTrue(delivered.await(1, TimeUnit.SECONDS));
        assertNotNull(captured.get());
        String json = captured.get().get(0).toJson();
        assertTrue(json.contains("\"parentSpanId\":\"" + parent.spanId() + "\""));
        assertTrue(json.contains("UPDATE orders SET status = ?"));
        assertFalse(json.contains("PAID"));
        assertEquals("JDBC", json.substring(json.indexOf("\"protocol\":\"") + 12, json.indexOf("\",\"direction")));
        reporter.close();
    }

    @Test
    void skipsConnectorMetadataQueries() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1, events -> delivered.countDown());
        JdbcEvidenceRuntime.initialize("order-service", reporter);
        TestContextHolder.enter("run", "case", "profile", 1);

        JdbcEvidenceRuntime.complete(new Object(), "SELECT TABLE_SCHEMA, NULL, TABLE_NAME, COLUMN_NAME "
                + "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND UPPER(DATA_TYPE) = ?", "SELECT",
                System.currentTimeMillis(), null);

        assertFalse(delivered.await(200, TimeUnit.MILLISECONDS));
        reporter.close();
    }

    @Test
    void skipsConfiguredSqlPrefix() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1, events -> delivered.countDown());
        JdbcEvidenceRuntime.initialize("order-service", reporter, Arrays.asList("SELECT 1"),
                Collections.<String>emptyList());
        TestContextHolder.enter("run", "case", "profile", 1);

        JdbcEvidenceRuntime.complete(new Object(), "SELECT 1", "SELECT", System.currentTimeMillis(), null);

        assertFalse(delivered.await(200, TimeUnit.MILLISECONDS));
        reporter.close();
    }

    @Test
    void skipsConfiguredSqlRegularExpression() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1, events -> delivered.countDown());
        JdbcEvidenceRuntime.initialize("order-service", reporter, Collections.<String>emptyList(),
                Arrays.asList("/\\* ping \\*/.*"));
        TestContextHolder.enter("run", "case", "profile", 1);

        JdbcEvidenceRuntime.complete(new Object(), "/* ping */ SELECT 1", "SELECT", System.currentTimeMillis(), null);

        assertFalse(delivered.await(200, TimeUnit.MILLISECONDS));
        reporter.close();
    }
}
