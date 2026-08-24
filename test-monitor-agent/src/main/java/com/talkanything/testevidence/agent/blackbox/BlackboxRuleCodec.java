package com.talkanything.testevidence.agent.blackbox;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlackboxRuleCodec {
    private BlackboxRuleCodec() { }

    public static List<BlackboxRule> decode(String response) {
        if (response == null || response.trim().isEmpty()) return Collections.emptyList();
        Map<String, Builder> groups = new LinkedHashMap<String, Builder>();
        String[] lines = response.split("\\r?\\n");
        for (int index = 0; index < lines.length; index++) decodeLine(lines[index], groups);
        List<BlackboxRule> rules = new ArrayList<BlackboxRule>();
        for (Builder builder : groups.values()) rules.add(builder.build());
        return Collections.unmodifiableList(rules);
    }

    private static void decodeLine(String line, Map<String, Builder> groups) {
        try {
            String[] values = line.split("\\t", -1);
            if (values.length != 10 && values.length != 11) return;
            for (int index = 0; index < values.length; index++) values[index] = decodeField(values[index]);
            String key = values[0] + "\u0000" + values[6];
            Builder builder = groups.get(key);
            if (builder == null) {
                builder = new Builder(values);
                groups.put(key, builder);
            }
            builder.add(values[7], locations(values[8]), values[9]);
        } catch (Exception ignored) { }
    }

    private static String decodeField(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static List<String> locations(String value) {
        if (value.length() < 4 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            return Collections.emptyList();
        }
        String content = value.substring(1, value.length() - 1).replace("\"", "").trim();
        if (content.isEmpty()) return Collections.emptyList();
        String[] values = content.split(",");
        List<String> locations = new ArrayList<String>();
        for (int index = 0; index < values.length; index++) locations.add(values[index].trim());
        return locations;
    }

    private static final class Builder {
        private final String ruleId;
        private final String runId;
        private final String caseId;
        private final String profileId;
        private final int profileVersion;
        private final long expiresAtMillis;
        private final List<BlackboxRule.Matcher> matchers = new ArrayList<BlackboxRule.Matcher>();
        private final boolean payloadCaptureEnabled;

        private Builder(String[] values) {
            ruleId = values[0]; runId = values[1]; caseId = values[2]; profileId = values[3];
            profileVersion = Integer.parseInt(values[4]); expiresAtMillis = Long.parseLong(values[5]);
            payloadCaptureEnabled = values.length == 11 && "true".equals(values[10]);
        }

        private void add(String field, List<String> locations, String value) {
            if (!field.isEmpty() && !locations.isEmpty()) matchers.add(new BlackboxRule.Matcher(field, locations, value));
        }

        private BlackboxRule build() {
            return new BlackboxRule(ruleId, runId, caseId, profileId, profileVersion, expiresAtMillis, matchers, payloadCaptureEnabled);
        }
    }
}
