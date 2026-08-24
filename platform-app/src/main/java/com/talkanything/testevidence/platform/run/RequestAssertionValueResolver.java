package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RequestAssertionValueResolver {
    private final ObjectMapper objectMapper;

    public RequestAssertionValueResolver(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public List<Object> resolve(MysqlScalarAssertionDefinition definition, JsonNode trigger) {
        List<Object> values = new ArrayList<>();
        for (MysqlScalarAssertionDefinition.Parameter parameter : definition.parameters()) {
            values.add("REQUEST_JSON_PATH".equals(parameter.source()) ? jsonValue(parameter, trigger) : headerValue(parameter, trigger));
        }
        return values;
    }

    private Object jsonValue(MysqlScalarAssertionDefinition.Parameter parameter, JsonNode trigger) {
        try {
            JsonNode body = objectMapper.readTree(trigger.path("body").asText());
            Object value = JsonPath.parse(body.toString()).read(parameter.jsonPath());
            if (value instanceof Collection<?> || value != null && value.getClass().isArray()) throw invalid();
            return value;
        } catch (Exception exception) { throw invalid(); }
    }

    private Object headerValue(MysqlScalarAssertionDefinition.Parameter parameter, JsonNode trigger) {
        Iterator<Map.Entry<String, JsonNode>> headers = trigger.path("headers").fields();
        while (headers.hasNext()) {
            Map.Entry<String, JsonNode> header = headers.next();
            if (header.getKey().equalsIgnoreCase(parameter.headerName())) return header.getValue().asText();
        }
        throw invalid();
    }

    private IllegalArgumentException invalid() { return new IllegalArgumentException("MYSQL_SCALAR request parameter did not resolve to one value"); }
}
