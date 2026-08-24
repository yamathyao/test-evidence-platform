package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.Assertion;
import org.springframework.stereotype.Component;

@Component
public class HttpStatusAssertionEvaluator {
    private final ObjectMapper objectMapper;

    public HttpStatusAssertionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AssertionEvaluation evaluate(Assertion assertion, int actualStatus) {
        String actual = "{\"actual\":" + actualStatus + "}";
        if (!"HTTP_STATUS".equals(assertion.getAssertionType())) {
            return failed("{}", actual, "Unsupported assertion type: " + assertion.getAssertionType());
        }
        try {
            JsonNode definition = objectMapper.readTree(assertion.getDefinitionJson());
            JsonNode expectedNode = definition.get("expected");
            if (expectedNode == null || !expectedNode.isIntegralNumber() || !expectedNode.canConvertToInt()) {
                return failed("{}", actual, "HTTP_STATUS expected is required");
            }
            int expected = expectedNode.intValue();
            String expectedJson = "{\"expected\":" + expected + "}";
            if (expected == actualStatus) return new AssertionEvaluation(AssertionResultStatus.PASSED, expectedJson, actual, null);
            return failed(expectedJson, actual, "Expected HTTP status " + expected + " but was " + actualStatus);
        } catch (Exception exception) {
            return failed("{}", actual, "HTTP_STATUS expected is required");
        }
    }

    private AssertionEvaluation failed(String expected, String actual, String reason) {
        return new AssertionEvaluation(AssertionResultStatus.FAILED, expected, actual, reason);
    }
}
