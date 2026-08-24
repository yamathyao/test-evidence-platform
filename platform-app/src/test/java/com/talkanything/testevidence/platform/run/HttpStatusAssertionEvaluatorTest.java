package com.talkanything.testevidence.platform.casefile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.run.AssertionEvaluation;
import com.talkanything.testevidence.platform.run.AssertionResultStatus;
import com.talkanything.testevidence.platform.run.HttpStatusAssertionEvaluator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpStatusAssertionEvaluatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpStatusAssertionEvaluator evaluator = new HttpStatusAssertionEvaluator(objectMapper);

    @Test
    void passesWhenExpectedStatusMatchesActualStatus() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_STATUS", "{\"expected\":201}"), 201);

        assertEquals(AssertionResultStatus.PASSED, result.status());
        assertEquals("{\"expected\":201}", result.expectedJson());
        assertEquals("{\"actual\":201}", result.actualJson());
    }

    @Test
    void failsWhenExpectedStatusDiffersFromActualStatus() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_STATUS", "{\"expected\":200}"), 201);

        assertEquals(AssertionResultStatus.FAILED, result.status());
        assertEquals("Expected HTTP status 200 but was 201", result.failureReason());
    }

    @Test
    void failsWhenExpectedStatusIsMissing() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_STATUS", "{}"), 201);

        assertEquals(AssertionResultStatus.FAILED, result.status());
        assertEquals("HTTP_STATUS expected is required", result.failureReason());
    }

    @Test
    void failsWhenExpectedStatusIsNotAnInteger() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("HTTP_STATUS", "{\"expected\":200.5}"), 200);

        assertEquals(AssertionResultStatus.FAILED, result.status());
        assertEquals("HTTP_STATUS expected is required", result.failureReason());
    }

    @Test
    void failsForUnsupportedAssertionType() throws Exception {
        AssertionEvaluation result = evaluator.evaluate(assertion("JSON_PATH", "{}"), 201);

        assertEquals(AssertionResultStatus.FAILED, result.status());
        assertEquals("Unsupported assertion type: JSON_PATH", result.failureReason());
    }

    private Assertion assertion(String type, String definition) throws Exception {
        return new Assertion(null, 1, type, definition);
    }
}
