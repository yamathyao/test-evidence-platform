package com.talkanything.testevidence.platform.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxCorrelationDefinitionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsCompleteMatcherGroupAndRejectsMissingOrUnknownFields() throws Exception {
        BlackboxCorrelationDefinition definition = BlackboxCorrelationDefinition.parse(objectMapper.readTree("{\"blackboxCorrelation\":{"
                + "\"defaultTtlSeconds\":600,\"retentionSeconds\":3600,\"singleUse\":true,"
                + "\"targetServices\":[\"order-service\"],\"matchGroups\":[{\"name\":\"order\",\"matchers\":["
                + "{\"field\":\"orderNo\",\"locations\":[\"QUERY\"],\"match\":\"EXACT\"},"
                + "{\"field\":\"tenantId\",\"locations\":[\"HEADER\"],\"match\":\"EXACT\"}]}]}}"));

        assertTrue(definition.accepts(values("orderNo", "ORD-1", "tenantId", "tenant-a")));
        assertFalse(definition.accepts(values("orderNo", "ORD-1")));
        assertFalse(definition.accepts(values("orderNo", "ORD-1", "tenantId", "tenant-a", "userId", "u1")));
    }

    @Test
    void rejectsUnsupportedMatcherLocation() throws Exception {
        String definition = "{\"blackboxCorrelation\":{\"defaultTtlSeconds\":600,\"retentionSeconds\":3600,"
                + "\"singleUse\":true,\"targetServices\":[\"order-service\"],\"matchGroups\":[{\"name\":\"order\","
                + "\"matchers\":[{\"field\":\"orderNo\",\"locations\":[\"COOKIE\"],\"match\":\"EXACT\"}]}]}}";

        assertThrows(IllegalArgumentException.class, () -> BlackboxCorrelationDefinition.parse(objectMapper.readTree(definition)));
    }

    @Test
    void acceptsAnOmittedTargetServiceScope() throws Exception {
        BlackboxCorrelationDefinition definition = BlackboxCorrelationDefinition.parse(objectMapper.readTree("{\"blackboxCorrelation\":{"
                + "\"defaultTtlSeconds\":60,\"retentionSeconds\":300,\"matchGroups\":[{\"name\":\"browser\","
                + "\"matchers\":[{\"field\":\"orderNo\",\"locations\":[\"JSON_BODY\"],\"match\":\"EXACT\"}]}]}}"));

        assertTrue(definition.targetServices().isEmpty());
    }

    private Map<String, String> values(String... pairs) {
        Map<String, String> values = new HashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) values.put(pairs[index], pairs[index + 1]);
        return values;
    }
}
