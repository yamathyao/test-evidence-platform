package com.talkanything.testevidence.platform.casefile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.run.AssertionEvaluation;
import com.talkanything.testevidence.platform.run.AssertionResultStatus;
import com.talkanything.testevidence.platform.run.HttpAssertionEvaluator;
import com.talkanything.testevidence.platform.run.HttpStatusAssertionEvaluator;
import com.talkanything.testevidence.platform.run.HttpTriggerResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpAssertionEvaluatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpAssertionEvaluator evaluator = new HttpAssertionEvaluator(objectMapper,
            new HttpStatusAssertionEvaluator(objectMapper));

    @Test
    void passesWhenJsonPathStringValueMatches() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_JSON_PATH",
                "{\"jsonPath\":\"$.data.orderNo\",\"expected\":\"ORD-001\"}"), response());

        assertEquals(AssertionResultStatus.PASSED, result.status());
        assertEquals("\"ORD-001\"", result.expectedJson());
        assertEquals("{\"jsonPath\":\"$.data.orderNo\",\"actual\":\"ORD-001\"}", result.actualJson());
    }

    @Test
    void passesWhenJsonPathNumberValueMatches() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_JSON_PATH",
                "{\"jsonPath\":\"$.data.amount\",\"expected\":12.50}"), response());

        assertEquals(AssertionResultStatus.PASSED, result.status());
        assertEquals("12.5", result.expectedJson());
        assertEquals("{\"jsonPath\":\"$.data.amount\",\"actual\":12.5}", result.actualJson());
    }

    @Test
    void passesWhenJsonPathBooleanValueMatches() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_JSON_PATH",
                "{\"jsonPath\":\"$.data.paid\",\"expected\":true}"), response());

        assertEquals(AssertionResultStatus.PASSED, result.status());
    }

    @Test
    void passesWhenJsonPathNullValueMatches() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_JSON_PATH",
                "{\"jsonPath\":\"$.data.cancelReason\",\"expected\":null}"), response());

        assertEquals(AssertionResultStatus.PASSED, result.status());
        assertEquals("{\"jsonPath\":\"$.data.cancelReason\",\"actual\":null}", result.actualJson());
    }

    @Test
    void failsWhenJsonPathDoesNotResolve() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_JSON_PATH",
                "{\"jsonPath\":\"$.data.missing\",\"expected\":\"ORD-001\"}"), response());

        assertEquals(AssertionResultStatus.FAILED, result.status());
        assertEquals("HTTP_JSON_PATH $.data.missing did not resolve to one value", result.failureReason());
    }

    @Test
    void failsWhenResponseBodyIsNotJson() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_JSON_PATH",
                "{\"jsonPath\":\"$.data.orderNo\",\"expected\":\"ORD-001\"}"),
                new HttpTriggerResponse(200, "not-json"));

        assertEquals(AssertionResultStatus.FAILED, result.status());
        assertEquals("HTTP_JSON_PATH response body is not valid JSON", result.failureReason());
    }

    @Test
    void preservesStatusEvaluatorResultForUnsupportedAssertionType() {
        AssertionEvaluation result = evaluator.evaluate(assertion("UNSUPPORTED", "{}"), response());

        assertEquals(AssertionResultStatus.FAILED, result.status());
        assertEquals("{\"actual\":200}", result.actualJson());
        assertEquals("Unsupported assertion type: UNSUPPORTED", result.failureReason());
    }

    private Assertion assertion(String type, String definition) {
        return new Assertion(null, 1, type, definition);
    }

    private HttpTriggerResponse response() {
        return new HttpTriggerResponse(200,
                "{\"data\":{\"orderNo\":\"ORD-001\",\"amount\":12.5,\"paid\":true,\"cancelReason\":null}}");
    }
}
