package com.talkanything.testevidence.agent.blackbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlackboxRequestValues {
    private final Map<String, Map<String, String>> values = new HashMap<String, Map<String, String>>();
    private final List<String> pathValues = new ArrayList<String>();

    public void put(String location, String field, String value) {
        if (location == null || field == null || value == null) return;
        Map<String, String> fields = values.get(location);
        if (fields == null) {
            fields = new HashMap<String, String>();
            values.put(location, fields);
        }
        fields.put(field(location, field), value);
    }

    public void addPathValue(String value) {
        if (value != null && !value.isEmpty()) pathValues.add(value);
    }

    public boolean matches(BlackboxRule.Matcher matcher) {
        for (int index = 0; index < matcher.locations().size(); index++) {
            String location = matcher.locations().get(index);
            if ("PATH".equals(location) && pathValues.contains(matcher.value())) return true;
            Map<String, String> fields = values.get(location);
            if (fields != null && matcher.value().equals(fields.get(field(location, matcher.field())))) return true;
        }
        return false;
    }

    private String field(String location, String value) {
        return "HEADER".equals(location) ? value.toLowerCase(Locale.ROOT) : value;
    }
}
