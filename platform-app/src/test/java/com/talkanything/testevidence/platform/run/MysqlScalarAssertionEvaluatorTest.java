package com.talkanything.testevidence.platform.casefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.run.AssertionEvaluation;
import com.talkanything.testevidence.platform.run.AssertionResultStatus;
import com.talkanything.testevidence.platform.run.MysqlScalarAssertionDefinitionParser;
import com.talkanything.testevidence.platform.run.MysqlScalarAssertionEvaluator;
import com.talkanything.testevidence.platform.run.MysqlScalarQueryClient;
import com.talkanything.testevidence.platform.run.MysqlScalarQueryException;
import com.talkanything.testevidence.platform.run.MysqlScalarValueType;
import com.talkanything.testevidence.platform.run.RequestAssertionValueResolver;
import com.talkanything.testevidence.platform.run.ScalarQueryResult;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MysqlScalarAssertionEvaluatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MysqlScalarAssertionDefinitionParser parser = new MysqlScalarAssertionDefinitionParser(objectMapper);
    private final RequestAssertionValueResolver resolver = new RequestAssertionValueResolver(objectMapper);

    @Test
    void comparesStringNumberBooleanAndNullWithoutPersistingRawValues() throws Exception {
        assertPassed("\"PAID\"", "PAID", MysqlScalarValueType.STRING);
        assertPassed("12.50", new BigDecimal("12.5"), MysqlScalarValueType.NUMBER);
        assertPassed("true", true, MysqlScalarValueType.BOOLEAN);
        assertPassed("null", null, MysqlScalarValueType.NULL);
    }

    @Test
    void reportsUnconfiguredDataSourceWithoutLeakingRequestValue() throws Exception {
        MysqlScalarAssertionEvaluator evaluator = evaluator(Optional.empty());

        AssertionEvaluation result = evaluator.evaluate(assertion("\"PAID\"", 0), trigger());

        assertEquals(AssertionResultStatus.FAILED, result.status());
        assertEquals("MYSQL_SCALAR data source is not configured", result.failureReason());
        assertFalse(result.expectedJson().contains("PAID"));
        assertFalse(result.actualJson().contains("order-1"));
    }

    @Test
    void retriesMismatchesUntilExpectedValueAppears() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        MysqlScalarQueryClient client = (sql, parameters, type) -> ScalarQueryResult.from(
                attempts.incrementAndGet() == 3 ? "PAID" : "PENDING", type);
        MysqlScalarAssertionEvaluator evaluator = evaluator(Optional.of(client));

        AssertionEvaluation result = evaluator.evaluate(assertion("\"PAID\"", 1), trigger());

        assertEquals(AssertionResultStatus.PASSED, result.status());
        assertEquals(3, attempts.get());
        assertFalse(result.actualJson().contains("PENDING"));
        assertFalse(result.actualJson().contains("PAID"));
    }

    @Test
    void failsImmediatelyWhenQueryClientReportsShapeError() throws Exception {
        MysqlScalarQueryClient client = (sql, parameters, type) -> {
            throw new MysqlScalarQueryException("MYSQL_SCALAR query did not return exactly one row and one column");
        };
        MysqlScalarAssertionEvaluator evaluator = evaluator(Optional.of(client));

        AssertionEvaluation result = evaluator.evaluate(assertion("\"PAID\"", 1), trigger());

        assertEquals(AssertionResultStatus.FAILED, result.status());
        assertEquals("MYSQL_SCALAR query did not return exactly one row and one column", result.failureReason());
    }

    private void assertPassed(String expected, Object actual, MysqlScalarValueType type) throws Exception {
        MysqlScalarQueryClient client = (sql, parameters, ignored) -> ScalarQueryResult.from(actual, type);
        AssertionEvaluation result = evaluator(Optional.of(client)).evaluate(assertion(expected, 0), trigger());

        assertEquals(AssertionResultStatus.PASSED, result.status());
        assertFalse(result.expectedJson().contains("PAID"));
        assertFalse(result.actualJson().contains("PAID"));
        assertFalse(result.actualJson().contains("order-1"));
    }

    private MysqlScalarAssertionEvaluator evaluator(Optional<MysqlScalarQueryClient> client) {
        return new MysqlScalarAssertionEvaluator(objectMapper, parser, resolver, client);
    }

    private Assertion assertion(String expected, int waitSeconds) {
        return new Assertion(null, 1, "MYSQL_SCALAR", """
                {"sql":"SELECT status FROM orders WHERE order_no = ?","parameters":[{"source":"REQUEST_JSON_PATH","jsonPath":"$.orderNo"}],"expected":%s,"waitSeconds":%d}
                """.formatted(expected, waitSeconds));
    }

    private com.fasterxml.jackson.databind.JsonNode trigger() throws Exception {
        return objectMapper.readTree("{\"body\":\"{\\\"orderNo\\\":\\\"order-1\\\"}\",\"headers\":{}}");
    }
}
