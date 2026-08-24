package com.talkanything.testevidence.platform.profile;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlackboxCorrelationDefinition {
    private static final int MIN_TTL_SECONDS = 60;
    private static final int MAX_TTL_SECONDS = 3600;
    private static final int MIN_RETENTION_SECONDS = 300;
    private static final int MAX_RETENTION_SECONDS = 86400;
    private static final int MAX_VALUE_BYTES = 256;
    private static final Set<String> SUPPORTED_LOCATIONS = Set.of("PATH", "QUERY", "HEADER", "FORM", "JSON_BODY");

    private final int defaultTtlSeconds;
    private final int retentionSeconds;
    private final List<String> targetServices;
    private final List<MatchGroup> matchGroups;

    private BlackboxCorrelationDefinition(int defaultTtlSeconds, int retentionSeconds, List<String> targetServices,
                                          List<MatchGroup> matchGroups) {
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.retentionSeconds = retentionSeconds;
        this.targetServices = targetServices;
        this.matchGroups = matchGroups;
    }

    public static BlackboxCorrelationDefinition parse(JsonNode root) {
        JsonNode correlation = root == null ? null : root.path("blackboxCorrelation");
        if (!correlation.isObject()) {
            throw new IllegalArgumentException("Invalid blackbox correlation definition");
        }
        int defaultTtlSeconds = range(correlation, "defaultTtlSeconds", MIN_TTL_SECONDS, MAX_TTL_SECONDS);
        int retentionSeconds = range(correlation, "retentionSeconds", MIN_RETENTION_SECONDS, MAX_RETENTION_SECONDS);
        List<String> targetServices = parseTargetServices(correlation.path("targetServices"));
        return new BlackboxCorrelationDefinition(defaultTtlSeconds, retentionSeconds, targetServices,
                parseGroups(correlation.path("matchGroups")));
    }

    public int defaultTtlSeconds() { return defaultTtlSeconds; }
    public int retentionSeconds() { return retentionSeconds; }
    public List<String> targetServices() { return targetServices; }

    public boolean accepts(Map<String, String> values) {
        if (values == null) return false;
        for (MatchGroup group : matchGroups) {
            if (group.accepts(values)) return true;
        }
        return false;
    }

    private static int range(JsonNode node, String field, int minimum, int maximum) {
        JsonNode value = node.path(field);
        if (!value.canConvertToInt() || value.intValue() < minimum || value.intValue() > maximum) {
            throw new IllegalArgumentException("Invalid blackbox correlation definition");
        }
        return value.intValue();
    }

    private static List<String> parseTargetServices(JsonNode services) {
        if (services.isMissingNode() || services.isNull()) return Collections.emptyList();
        if (!services.isArray()) throw new IllegalArgumentException("Invalid blackbox correlation definition");
        List<String> values = new ArrayList<String>();
        for (JsonNode service : services) {
            if (!service.isTextual() || service.textValue().trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid blackbox correlation definition");
            }
            values.add(service.textValue());
        }
        return Collections.unmodifiableList(values);
    }

    private static List<MatchGroup> parseGroups(JsonNode groups) {
        if (!groups.isArray() || groups.isEmpty()) {
            throw new IllegalArgumentException("Invalid blackbox correlation definition");
        }
        List<MatchGroup> parsed = new ArrayList<MatchGroup>();
        Set<String> groupNames = new HashSet<String>();
        for (JsonNode group : groups) {
            String name = requiredText(group, "name");
            if (!groupNames.add(name)) throw new IllegalArgumentException("Invalid blackbox correlation definition");
            parsed.add(new MatchGroup(parseMatchers(group.path("matchers"))));
        }
        return Collections.unmodifiableList(parsed);
    }

    private static Set<String> parseMatchers(JsonNode matchers) {
        if (!matchers.isArray() || matchers.isEmpty()) {
            throw new IllegalArgumentException("Invalid blackbox correlation definition");
        }
        Set<String> fields = new HashSet<String>();
        for (JsonNode matcher : matchers) {
            String field = requiredText(matcher, "field");
            if (!fields.add(field) || !"EXACT".equals(matcher.path("match").asText())) {
                throw new IllegalArgumentException("Invalid blackbox correlation definition");
            }
            JsonNode locations = matcher.path("locations");
            if (!locations.isArray() || locations.isEmpty()) {
                throw new IllegalArgumentException("Invalid blackbox correlation definition");
            }
            for (JsonNode location : locations) {
                if (!location.isTextual() || !SUPPORTED_LOCATIONS.contains(location.textValue())) {
                    throw new IllegalArgumentException("Invalid blackbox correlation definition");
                }
            }
        }
        return Collections.unmodifiableSet(fields);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid blackbox correlation definition");
        }
        return value.textValue();
    }

    private static final class MatchGroup {
        private final Set<String> fields;

        private MatchGroup(Set<String> fields) {
            this.fields = fields;
        }

        private boolean accepts(Map<String, String> values) {
            if (!fields.equals(values.keySet())) return false;
            for (String value : values.values()) {
                if (value == null || value.trim().isEmpty()
                        || value.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) return false;
            }
            return true;
        }
    }
}
