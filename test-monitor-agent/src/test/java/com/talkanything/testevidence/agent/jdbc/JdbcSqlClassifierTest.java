package com.talkanything.testevidence.agent.jdbc;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcSqlClassifierTest {
    @Test
    void retainsBusinessSelectWithoutIgnoreRules() {
        assertFalse(JdbcSqlClassifier.shouldIgnore("SELECT * FROM orders WHERE order_no = ?",
                Collections.<String>emptyList(), Collections.<String>emptyList()));
    }

    @Test
    void ignoresConfiguredPrefixAfterSqlNormalization() {
        assertTrue(JdbcSqlClassifier.shouldIgnore("  select   1  ", Arrays.asList("SELECT 1"),
                Collections.<String>emptyList()));
    }

    @Test
    void ignoresConfiguredRegularExpressionAgainstOriginalSql() {
        assertTrue(JdbcSqlClassifier.shouldIgnore("/* ping */ SELECT 1", Collections.<String>emptyList(),
                Arrays.asList("/\\* ping \\*/.*")));
    }
}
