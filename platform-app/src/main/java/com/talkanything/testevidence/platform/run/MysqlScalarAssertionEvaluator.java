package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.talkanything.testevidence.platform.casefile.Assertion;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MysqlScalarAssertionEvaluator {
    private static final String MISMATCH = "MYSQL_SCALAR value did not match expected";
    private final ObjectMapper objectMapper;
    private final MysqlScalarAssertionDefinitionParser definitionParser;
    private final RequestAssertionValueResolver valueResolver;
    private final Optional<MysqlScalarQueryClient> queryClient;

    public MysqlScalarAssertionEvaluator(ObjectMapper objectMapper, MysqlScalarAssertionDefinitionParser definitionParser,
                                         RequestAssertionValueResolver valueResolver,
                                         Optional<MysqlScalarQueryClient> queryClient) {
        this.objectMapper = objectMapper;
        this.definitionParser = definitionParser;
        this.valueResolver = valueResolver;
        this.queryClient = queryClient;
    }

    public AssertionEvaluation evaluate(Assertion assertion, JsonNode trigger) {
        try {
            MysqlScalarAssertionDefinition definition = definitionParser.parse(objectMapper.readTree(assertion.getDefinitionJson()));
            ScalarQueryResult expected = ScalarQueryResult.from(value(definition.expected()), type(definition.expected()));
            if (queryClient.isEmpty()) return failed(expected, null, 0, "MYSQL_SCALAR data source is not configured");
            List<Object> parameters = valueResolver.resolve(definition, trigger);
            return query(definition, expected, parameters);
        } catch (IllegalArgumentException exception) {
            return failed(null, null, 0, "Invalid MYSQL_SCALAR assertion");
        } catch (Exception exception) {
            return failed(null, null, 0, "MYSQL_SCALAR assertion failed: " + exception.getClass().getSimpleName());
        }
    }

    private AssertionEvaluation query(MysqlScalarAssertionDefinition definition, ScalarQueryResult expected,
                                      List<Object> parameters) {
        long deadline = System.nanoTime() + definition.waitSeconds() * 1_000_000_000L;
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                ScalarQueryResult actual = queryClient.orElseThrow().query(definition.sql(), parameters, expected.type());
                if (matches(expected, actual)) return passed(expected, actual, attempts);
                if (System.nanoTime() >= deadline) return failed(expected, actual, attempts, MISMATCH);
            } catch (MysqlScalarQueryException exception) {
                return failed(expected, null, attempts, exception.getMessage());
            } catch (SQLException exception) {
                return failed(expected, null, attempts, "MYSQL_SCALAR query failed: SQLException");
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return failed(expected, null, attempts, "MYSQL_SCALAR assertion polling was interrupted");
            }
        }
    }

    private boolean matches(ScalarQueryResult expected, ScalarQueryResult actual) {
        if (expected.type() != actual.type()) return false;
        return switch (expected.type()) {
            case STRING, BOOLEAN, NULL -> java.util.Objects.equals(expected.value(), actual.value());
            case NUMBER -> actual.value() instanceof BigDecimal actualNumber
                    && ((BigDecimal) expected.value()).compareTo(actualNumber) == 0;
        };
    }

    private MysqlScalarValueType type(JsonNode value) {
        if (value.isNull()) return MysqlScalarValueType.NULL;
        if (value.isTextual()) return MysqlScalarValueType.STRING;
        if (value.isBoolean()) return MysqlScalarValueType.BOOLEAN;
        return MysqlScalarValueType.NUMBER;
    }

    private Object value(JsonNode value) {
        if (value.isNull()) return null;
        if (value.isTextual()) return value.asText();
        if (value.isBoolean()) return value.booleanValue();
        return value.decimalValue();
    }

    private AssertionEvaluation passed(ScalarQueryResult expected, ScalarQueryResult actual, int attempts) {
        return new AssertionEvaluation(AssertionResultStatus.PASSED, summary(expected), actual(actual, true, attempts), null);
    }

    private AssertionEvaluation failed(ScalarQueryResult expected, ScalarQueryResult actual, int attempts, String reason) {
        return new AssertionEvaluation(AssertionResultStatus.FAILED, expected == null ? "{}" : summary(expected),
                actual(actual, false, attempts), reason);
    }

    private String summary(ScalarQueryResult value) {
        return objectMapper.createObjectNode().put("type", value.type().name().toLowerCase())
                .put("length", value.length()).put("sha256", value.sha256()).toString();
    }

    private String actual(ScalarQueryResult value, boolean matched, int attempts) {
        ObjectNode result = objectMapper.createObjectNode().put("matched", matched).put("attempts", attempts);
        if (value != null) result.put("type", value.type().name().toLowerCase())
                .put("length", value.length()).put("sha256", value.sha256());
        return result.toString();
    }
}
