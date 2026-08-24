package com.talkanything.testevidence.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AgentConfiguration {
    private final String collectorUrl;
    private final String serviceName;
    private final String token;
    private final List<String> jdbcIgnoreSqlPrefixes;
    private final List<String> jdbcIgnoreSqlRegexes;

    private AgentConfiguration(String collectorUrl, String serviceName, String token,
                               List<String> jdbcIgnoreSqlPrefixes, List<String> jdbcIgnoreSqlRegexes) {
        this.collectorUrl = collectorUrl;
        this.serviceName = serviceName;
        this.token = token;
        this.jdbcIgnoreSqlPrefixes = jdbcIgnoreSqlPrefixes;
        this.jdbcIgnoreSqlRegexes = jdbcIgnoreSqlRegexes;
    }

    static AgentConfiguration parse(String arguments) {
        Map<String, String> values = new HashMap<String, String>();
        for (String entry : arguments.split(",")) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2 || pair[0].trim().isEmpty() || pair[1].trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid agent argument: " + entry);
            }
            values.put(pair[0].trim(), pair[1].trim());
        }
        Set<String> requiredKeys = new HashSet<String>(Arrays.asList("collector", "service", "token"));
        Set<String> supportedKeys = new HashSet<String>(requiredKeys);
        supportedKeys.add("jdbcIgnoreSqlPrefixes");
        supportedKeys.add("jdbcIgnoreSqlRegexes");
        if (!values.keySet().containsAll(requiredKeys) || !supportedKeys.containsAll(values.keySet())) {
            throw new IllegalArgumentException("Agent arguments must contain collector, service, token and optional JDBC rules");
        }
        return new AgentConfiguration(values.get("collector"), values.get("service"), values.get("token"),
                parseRules(values.get("jdbcIgnoreSqlPrefixes")), parseRules(values.get("jdbcIgnoreSqlRegexes")));
    }

    private static List<String> parseRules(String value) {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        List<String> rules = new ArrayList<String>();
        for (String rule : value.split("\\|")) {
            if (!rule.trim().isEmpty()) rules.add(rule.trim());
        }
        return Collections.unmodifiableList(rules);
    }

    String collectorUrl() {
        return collectorUrl;
    }

    String serviceName() {
        return serviceName;
    }
    String token() { return token; }
    List<String> jdbcIgnoreSqlPrefixes() { return jdbcIgnoreSqlPrefixes; }
    List<String> jdbcIgnoreSqlRegexes() { return jdbcIgnoreSqlRegexes; }
}
