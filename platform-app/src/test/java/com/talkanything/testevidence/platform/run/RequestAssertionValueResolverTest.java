package com.talkanything.testevidence.platform.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestAssertionValueResolverTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MysqlScalarAssertionDefinitionParser parser = new MysqlScalarAssertionDefinitionParser(objectMapper);
    private final RequestAssertionValueResolver resolver = new RequestAssertionValueResolver(objectMapper);

    @Test
    void resolvesJsonBodyAndHeaderParameters() throws Exception {
        MysqlScalarAssertionDefinition definition = parser.parse(objectMapper.readTree("""
                {"sql":"SELECT 1 WHERE ? = ?","parameters":[{"source":"REQUEST_JSON_PATH","jsonPath":"$.orderNo"},{"source":"REQUEST_HEADER","headerName":"X-Tenant"}],"expected":1}
                """));
        var trigger = objectMapper.readTree("""
                {"body":"{\\"orderNo\\":\\"order-1\\"}","headers":{"x-tenant":"tenant-a"}}
                """);

        assertEquals(List.of("order-1", "tenant-a"), resolver.resolve(definition, trigger));
    }

    @Test
    void rejectsMissingHeaderAndMultipleJsonPathValues() throws Exception {
        MysqlScalarAssertionDefinition header = parser.parse(objectMapper.readTree("""
                {"sql":"SELECT ?","parameters":[{"source":"REQUEST_HEADER","headerName":"X-Tenant"}],"expected":1}
                """));
        MysqlScalarAssertionDefinition array = parser.parse(objectMapper.readTree("""
                {"sql":"SELECT ?","parameters":[{"source":"REQUEST_JSON_PATH","jsonPath":"$.items[*]"}],"expected":1}
                """));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(header, objectMapper.readTree("{}")));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(array, objectMapper.readTree("{\"body\":\"{\\\"items\\\":[1,2]}\"}")));
    }
}
