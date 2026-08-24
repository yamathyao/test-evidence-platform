package com.talkanything.testevidence.agent.blackbox;

import java.util.Map;

public final class BlackboxValueMatcher {
    public boolean match(BlackboxRule rule, Map<String, String> values) {
        if (rule == null || values == null || rule.expired(System.currentTimeMillis()) || rule.matchers().isEmpty()) {
            return false;
        }
        for (BlackboxRule.Matcher matcher : rule.matchers()) {
            if (!matcher.value().equals(values.get(matcher.field()))) return false;
        }
        return true;
    }

    public boolean matchRequest(BlackboxRule rule, BlackboxRequestValues values) {
        if (rule == null || values == null || rule.expired(System.currentTimeMillis()) || rule.matchers().isEmpty()) {
            return false;
        }
        for (BlackboxRule.Matcher matcher : rule.matchers()) {
            if (!values.matches(matcher)) return false;
        }
        return true;
    }
}
