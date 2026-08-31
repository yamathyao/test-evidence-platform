package com.talkanything.testevidence.agent.jdbc;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import com.talkanything.testevidence.agent.http.AsyncEvidenceReporter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcStatementExecutionAdviceTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void reportsDirectSqlWithoutPreparedParameters() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> json = new AtomicReference<String>();
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1, (events, diagnostics) -> capture(events, json, delivered));
        JdbcEvidenceRuntime.initialize("order-service", reporter);
        TestContextHolder.enter("run", "case", "profile", 1);

        JdbcStatementExecutionAdvice.ExecutionState state = JdbcStatementExecutionAdvice.enter(
                new com.mysql.cj.jdbc.StatementImpl(),
                new Object[]{"UPDATE orders SET status = 'PAID' WHERE order_no = 123456"},
                "executeUpdate");
        JdbcStatementExecutionAdvice.exit(state, null);

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertTrue(json.get().contains("\"jdbcParameters\":[]"));
        assertTrue(json.get().contains("UPDATE orders SET status = '?' WHERE order_no = ?"));
        reporter.close();
    }

    @Test
    void classifiesStatementOperationsFromSqlAndMethod() {
        assertOperation("SELECT", "executeUpdate", "SELECT * FROM orders");
        assertOperation("INSERT", "execute", "INSERT INTO orders (order_no) VALUES ('ORD-1')");
        assertOperation("UPDATE", "executeUpdate", "UPDATE orders SET status = 'PAID'");
        assertOperation("DELETE", "execute", "DELETE FROM orders WHERE order_no = 'ORD-1'");
    }

    @Test
    void reportsOnlyTheOutermostNestedStatementExecution() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Integer> count = new AtomicReference<Integer>(0);
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1,
                (events, diagnostics) -> countJdbcEvents(events, count, delivered));
        JdbcEvidenceRuntime.initialize("order-service", reporter);
        TestContextHolder.enter("run", "case", "profile", 1);

        JdbcStatementExecutionAdvice.ExecutionState outer = JdbcStatementExecutionAdvice.enter(
                new com.mysql.jdbc.StatementImpl(), new Object[]{"UPDATE orders SET status = 'PAID'"}, "executeUpdate");
        JdbcStatementExecutionAdvice.ExecutionState inner = JdbcStatementExecutionAdvice.enter(
                new com.mysql.jdbc.StatementImpl(), new Object[]{"UPDATE orders SET status = 'PAID'"}, "executeUpdate");

        assertTrue(outer.root());
        assertFalse(inner.root());
        JdbcStatementExecutionAdvice.exit(inner, null);
        JdbcStatementExecutionAdvice.exit(outer, null);

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(1, count.get().intValue());
        reporter.close();
    }

    private void assertOperation(String expected, String method, String sql) {
        JdbcStatementExecutionAdvice.ExecutionState state = JdbcStatementExecutionAdvice.enter(
                new com.mysql.cj.jdbc.StatementImpl(), new Object[]{sql}, method);
        try {
            assertEquals(expected, state.operation());
        } finally {
            JdbcStatementExecutionAdvice.exit(state, null);
        }
    }

    private static void capture(List<EvidencePayload> events, AtomicReference<String> json, CountDownLatch delivered) {
        for (EvidencePayload event : events) {
            if (event.toJson().contains("\"protocol\":\"JDBC\"")) {
                json.set(event.toJson());
                delivered.countDown();
            }
        }
    }

    private static void countJdbcEvents(List<EvidencePayload> events, AtomicReference<Integer> count,
                                        CountDownLatch delivered) {
        for (EvidencePayload event : events) {
            if (event.toJson().contains("\"protocol\":\"JDBC\"")) {
                count.updateAndGet(value -> Integer.valueOf(value.intValue() + 1));
                delivered.countDown();
            }
        }
    }
}