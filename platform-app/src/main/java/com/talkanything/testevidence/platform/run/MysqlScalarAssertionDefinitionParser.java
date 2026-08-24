package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MysqlScalarAssertionDefinitionParser {
    public MysqlScalarAssertionDefinitionParser(com.fasterxml.jackson.databind.ObjectMapper objectMapper) { }

    public MysqlScalarAssertionDefinition parse(JsonNode value) {
        if (value == null || !value.isObject()) throw invalid();
        String sql = text(value, "sql");
        JsonNode expected = value.get("expected");
        if (!select(sql) || expected == null || !(expected.isValueNode())) throw invalid();
        List<MysqlScalarAssertionDefinition.Parameter> parameters = parameters(value.get("parameters"));
        if (placeholders(sql) != parameters.size()) throw invalid();
        int waitSeconds = value.path("waitSeconds").asInt(0);
        if (waitSeconds < 0 || waitSeconds > 10) throw invalid();
        return new MysqlScalarAssertionDefinition(sql.trim(), List.copyOf(parameters), expected, waitSeconds);
    }

    private List<MysqlScalarAssertionDefinition.Parameter> parameters(JsonNode values) {
        if (values == null || !values.isArray()) throw invalid();
        List<MysqlScalarAssertionDefinition.Parameter> result = new ArrayList<>();
        for (JsonNode value : values) {
            String source = text(value, "source");
            if (!"REQUEST_JSON_PATH".equals(source) && !"REQUEST_HEADER".equals(source)) throw invalid();
            String jsonPath = "REQUEST_JSON_PATH".equals(source) ? text(value, "jsonPath") : null;
            String headerName = "REQUEST_HEADER".equals(source) ? text(value, "headerName") : null;
            if (jsonPath == null && headerName == null) throw invalid();
            result.add(new MysqlScalarAssertionDefinition.Parameter(source, jsonPath, headerName));
        }
        return result;
    }

    private boolean select(String sql) {
        if (sql == null) return false;
        String upper = sql.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("SELECT ") && !upper.contains(";") && !upper.contains("--") && !upper.contains("/*") && !upper.contains("*/")
                && !upper.matches(".*\\b(INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|CALL|EXECUTE)\\b.*");
    }

    private int placeholders(String sql) {
        boolean quoted = false;
        int count = 0;
        for (int index = 0; index < sql.length(); index++) {
            char character = sql.charAt(index);
            if (character == '\'') {
                if (quoted && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') index++;
                else quoted = !quoted;
            } else if (!quoted && character == '?') count++;
        }
        return count;
    }
    private String text(JsonNode value, String name) { JsonNode item = value.get(name); return item != null && item.isTextual() && !item.asText().isBlank() ? item.asText() : null; }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid MYSQL_SCALAR assertion"); }
}
