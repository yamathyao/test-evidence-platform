package com.talkanything.testevidence.agent;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentConfigurationTest {
    @Test
    void parsesCollectorServiceAndToken() {
        AgentConfiguration configuration = AgentConfiguration.parse(
                "collector=http://127.0.0.1:8080,service=order-service,token=agent-token");

        assertEquals("http://127.0.0.1:8080", configuration.collectorUrl());
        assertEquals("order-service", configuration.serviceName());
        assertEquals("agent-token", configuration.token());
    }

    @Test
    void parsesOptionalJdbcIgnoreRulesWithoutChangingRequiredArguments() {
        AgentConfiguration configuration = AgentConfiguration.parse(
                "collector=http://127.0.0.1:8080,service=order-service,token=agent-token,"
                        + "jdbcIgnoreSqlPrefixes=SELECT 1|SHOW WARNINGS,"
                        + "jdbcIgnoreSqlRegexes=/\\* ping \\*/.*");

        assertEquals(Arrays.asList("SELECT 1", "SHOW WARNINGS"), configuration.jdbcIgnoreSqlPrefixes());
        assertEquals(Arrays.asList("/\\* ping \\*/.*"), configuration.jdbcIgnoreSqlRegexes());
    }
}
