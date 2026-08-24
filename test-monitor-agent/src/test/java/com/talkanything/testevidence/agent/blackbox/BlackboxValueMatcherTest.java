package com.talkanything.testevidence.agent.blackbox;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxValueMatcherTest {
    @Test
    void requiresAllGroupFieldsAndRejectsExpiredRules() {
        BlackboxRule active = new BlackboxRule("rule", "run", "case", "profile", 1, System.currentTimeMillis() + 10000,
                Arrays.asList(new BlackboxRule.Matcher("orderNo", Arrays.asList("QUERY"), "ORD-1"),
                        new BlackboxRule.Matcher("tenantId", Arrays.asList("HEADER"), "tenant-a")));
        BlackboxRule expired = new BlackboxRule("expired", "run", "case", "profile", 1, System.currentTimeMillis() - 1,
                Arrays.asList(new BlackboxRule.Matcher("orderNo", Arrays.asList("QUERY"), "ORD-1")));
        BlackboxValueMatcher matcher = new BlackboxValueMatcher();

        assertTrue(matcher.match(active, values("orderNo", "ORD-1", "tenantId", "tenant-a")));
        assertFalse(matcher.match(active, values("orderNo", "ORD-1")));
        assertFalse(matcher.match(expired, values("orderNo", "ORD-1")));
    }

    @Test
    void requiresValuesToAppearAtConfiguredLocations() {
        BlackboxRule rule = new BlackboxRule("rule", "run", "case", "profile", 1, System.currentTimeMillis() + 10000,
                Arrays.asList(new BlackboxRule.Matcher("orderNo", Arrays.asList("QUERY"), "ORD-1"),
                        new BlackboxRule.Matcher("tenantId", Arrays.asList("HEADER"), "tenant-a")));
        BlackboxRequestValues values = new BlackboxRequestValues();
        values.put("QUERY", "orderNo", "ORD-1");
        values.put("HEADER", "tenantId", "tenant-a");

        assertTrue(new BlackboxValueMatcher().matchRequest(rule, values));
        BlackboxRequestValues wrongLocation = new BlackboxRequestValues();
        wrongLocation.put("QUERY", "orderNo", "ORD-1");
        wrongLocation.put("QUERY", "tenantId", "tenant-a");
        assertFalse(new BlackboxValueMatcher().matchRequest(rule, wrongLocation));
    }

    private Map<String, String> values(String... pairs) {
        Map<String, String> values = new HashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) values.put(pairs[index], pairs[index + 1]);
        return values;
    }
}
