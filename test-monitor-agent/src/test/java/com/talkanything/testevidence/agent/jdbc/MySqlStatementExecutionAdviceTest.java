package com.talkanything.testevidence.agent.jdbc;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import com.talkanything.testevidence.agent.http.AsyncEvidenceReporter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlStatementExecutionAdviceTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void readsPreparedSqlWhenOriginalSqlIsUnavailable() {
        String sql = MySqlStatementExecutionAdvice.originalSql(new ConnectorJ8Statement());

        assertEquals("UPDATE test_evidence_p6_orders SET status = ? WHERE order_no = ?", sql);
    }

    @Test
    void classifiesWriteOperationsFromSqlInsteadOfJdbcMethodName() {
        assertOperation("DELETE", new DeleteStatement());
        assertOperation("INSERT", new InsertStatement());
        assertOperation("UPDATE", new ConnectorJ8Statement());
    }

    @Test
    void reportsOnlyTheOutermostNestedJdbcExecution() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicInteger jdbcEvents = new AtomicInteger();
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(4, 1,
                events -> countJdbcEvents(events, jdbcEvents, delivered));
        JdbcEvidenceRuntime.initialize("order-service", reporter);
        TestContextHolder.enter("run", "case", "profile", 1);

        MySqlStatementExecutionAdvice.ExecutionState outer =
                MySqlStatementExecutionAdvice.enter(new ConnectorJ8Statement(), "executeUpdate");
        MySqlStatementExecutionAdvice.ExecutionState inner =
                MySqlStatementExecutionAdvice.enter(new ConnectorJ8Statement(), "executeUpdate");

        assertTrue(outer.root());
        assertFalse(inner.root());
        MySqlStatementExecutionAdvice.exit(inner, null);
        MySqlStatementExecutionAdvice.exit(outer, null);

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(1, jdbcEvents.get());
        reporter.close();
    }

    @Test
    void clearsNestedScopeWhenInnerExecutionFails() {
        MySqlStatementExecutionAdvice.ExecutionState outer =
                MySqlStatementExecutionAdvice.enter(new ConnectorJ8Statement(), "executeUpdate");
        MySqlStatementExecutionAdvice.ExecutionState inner =
                MySqlStatementExecutionAdvice.enter(new ConnectorJ8Statement(), "executeUpdate");

        MySqlStatementExecutionAdvice.exit(inner, new IllegalStateException("failed"));
        MySqlStatementExecutionAdvice.exit(outer, null);

        MySqlStatementExecutionAdvice.ExecutionState direct =
                MySqlStatementExecutionAdvice.enter(new ConnectorJ8Statement(), "executeUpdate");
        assertTrue(direct.root());
        MySqlStatementExecutionAdvice.exit(direct, null);
    }

    private static void countJdbcEvents(List<EvidencePayload> events, AtomicInteger count, CountDownLatch delivered) {
        for (EvidencePayload event : events) {
            if (event.toJson().contains("\"protocol\":\"JDBC\"")) {
                count.incrementAndGet();
                delivered.countDown();
            }
        }
    }

    private void assertOperation(String expected, Object statement) {
        MySqlStatementExecutionAdvice.ExecutionState state = MySqlStatementExecutionAdvice.enter(statement, "executeUpdate");
        try {
            assertEquals(expected, state.operation);
        } finally {
            MySqlStatementExecutionAdvice.exit(state, null);
        }
    }

    public static final class ConnectorJ8Statement {
        public String getPreparedSql() {
            return "UPDATE test_evidence_p6_orders SET status = ? WHERE order_no = ?";
        }
    }

    public static final class DeleteStatement {
        public String getPreparedSql() {
            return "DELETE FROM orders WHERE order_no = ?";
        }
    }

    public static final class InsertStatement {
        public String getPreparedSql() {
            return "INSERT INTO orders (order_no) VALUES (?)";
        }
    }
}
