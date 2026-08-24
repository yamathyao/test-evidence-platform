package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.talkanything.testevidence.platform.casefile.Assertion;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class HttpAssertionEvaluator {
    private final ObjectMapper objectMapper;
    private final HttpStatusAssertionEvaluator statusEvaluator;

    public HttpAssertionEvaluator(ObjectMapper objectMapper, HttpStatusAssertionEvaluator statusEvaluator) {
        this.objectMapper = objectMapper;
        this.statusEvaluator = statusEvaluator;
    }

    public AssertionEvaluation evaluate(Assertion assertion, HttpTriggerResponse response) {
        if ("HTTP_STATUS".equals(assertion.getAssertionType())) {
            return statusEvaluator.evaluate(assertion, response.statusCode());
        }
        if (!"HTTP_JSON_PATH".equals(assertion.getAssertionType())) return statusEvaluator.evaluate(assertion, response.statusCode());
        return evaluateJsonPath(assertion, response.body());
    }

    private AssertionEvaluation evaluateJsonPath(Assertion assertion, String body) {
        JsonNode definition;
        try {
            definition = objectMapper.readTree(assertion.getDefinitionJson());
        } catch (Exception exception) {
            return failed("{}", "{}", "HTTP_JSON_PATH jsonPath is required");
        }
        JsonNode pathNode = definition.get("jsonPath");
        if (pathNode == null || !pathNode.isTextual() || pathNode.asText().isBlank()) {
            return failed("{}", "{}", "HTTP_JSON_PATH jsonPath is required");
        }
        String path = pathNode.asText();
        String actualWithoutValue = actual(path, null);
        JsonNode expected = definition.get("expected");
        if (expected == null) {
            return failed("{}", actualWithoutValue, "HTTP_JSON_PATH expected is required");
        }
        String expectedJson = expected.toString();
        try {
            objectMapper.readTree(body);
        } catch (Exception exception) {
            return failed(expectedJson, actualWithoutValue, "HTTP_JSON_PATH response body is not valid JSON");
        }
        try {
            Object value = JsonPath.parse(body).read(path);
            if (value instanceof Collection<?> || value != null && value.getClass().isArray()) {
                return failed(expectedJson, actualWithoutValue,
                        "HTTP_JSON_PATH " + path + " did not resolve to one value");
            }
            JsonNode actual = objectMapper.valueToTree(value);
            String actualJson = actual(path, actual);
            if (expected.equals(actual)) {
                return new AssertionEvaluation(AssertionResultStatus.PASSED, expectedJson, actualJson, null);
            }
            return failed(expectedJson, actualJson,
                    "Expected JSONPath " + path + " value " + expected + " but was " + actual);
        } catch (PathNotFoundException exception) {
            return failed(expectedJson, actualWithoutValue,
                    "HTTP_JSON_PATH " + path + " did not resolve to one value");
        } catch (InvalidPathException exception) {
            return failed(expectedJson, actualWithoutValue, "HTTP_JSON_PATH jsonPath is invalid");
        }
    }

    private String actual(String path, JsonNode value) {
        ObjectNode result = objectMapper.createObjectNode().put("jsonPath", path);
        if (value != null) result.set("actual", value);
        return result.toString();
    }

    private AssertionEvaluation failed(String expected, String actual, String reason) {
        return new AssertionEvaluation(AssertionResultStatus.FAILED, expected, actual, reason);
    }
}
