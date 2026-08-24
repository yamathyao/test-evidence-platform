package com.talkanything.testevidence.agent.jdbc;

import com.talkanything.testevidence.agent.context.TestContext;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcEvidenceTest {
    @Test
    void masksStatementSqlAndSerializesOnlyParameterSummary() {
        String sql = "SELECT * FROM orders WHERE order_no = 'ORD-001' AND buyer_id = 123456";
        assertEquals("SELECT * FROM orders WHERE order_no = '?' AND buyer_id = ?", JdbcEvidence.maskStatementSql(sql));
        assertEquals("5b75daab06a2ef21", JdbcValueSummary.of(1, "setString", "ORD-001").sha256());

        TestContext context = new TestContext("run", "case", "profile", 1, "trace", "span", "parent");
        JdbcEvidence evidence = new JdbcEvidence(context, "order-service", "SELECT", "SELECT * FROM orders WHERE order_no = ?",
                Collections.singletonList(JdbcValueSummary.of(1, "setString", "ORD-001")), Instant.parse("2026-08-14T00:00:00Z"), 5, "");

        String json = evidence.toJson();
        assertTrue(json.contains("\"protocol\":\"JDBC\""));
        assertTrue(json.contains("\"jdbcOperation\":\"SELECT\""));
        assertTrue(json.contains("\"sha256\":\"5b75daab06a2ef21\""));
        assertFalse(json.contains("ORD-001"));
    }
}
