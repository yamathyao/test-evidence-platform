package com.talkanything.testevidence.platform.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MysqlScalarAssertionDefinitionParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MysqlScalarAssertionDefinitionParser parser = new MysqlScalarAssertionDefinitionParser(objectMapper);

    @Test
    void acceptsSelectWithMatchingParameterCount() throws Exception {
        MysqlScalarAssertionDefinition definition = parser.parse(objectMapper.readTree("""
                {"sql":"SELECT status FROM orders WHERE order_no = ?","parameters":[{"source":"REQUEST_JSON_PATH","jsonPath":"$.orderNo"}],"expected":"PAID","waitSeconds":5}
                """));

        assertEquals(1, definition.parameters().size());
        assertEquals(5, definition.waitSeconds());
    }

    @Test
    void ignoresQuestionMarksInsideSqlStringLiterals() throws Exception {
        MysqlScalarAssertionDefinition definition = parser.parse(objectMapper.readTree("""
                {"sql":"SELECT '?' FROM orders WHERE order_no = ?","parameters":[{"source":"REQUEST_JSON_PATH","jsonPath":"$.orderNo"}],"expected":"PAID"}
                """));

        assertEquals(1, definition.parameters().size());
    }

    @Test
    void rejectsWriteSqlAndMismatchedParameters() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(objectMapper.readTree("""
                {"sql":"UPDATE orders SET status = ?","parameters":[],"expected":"PAID"}
                """)));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(objectMapper.readTree("""
                {"sql":"SELECT status FROM orders WHERE order_no = ?","parameters":[],"expected":"PAID"}
                """)));
    }

    @Test
    void rejectsSqlCommentEndMarker() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(objectMapper.readTree("""
                {"sql":"SELECT status FROM orders */","parameters":[],"expected":"PAID"}
                """)));
    }
}
