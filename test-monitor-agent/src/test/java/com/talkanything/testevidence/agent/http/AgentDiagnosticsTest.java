package com.talkanything.testevidence.agent.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentDiagnosticsTest {
    @Test
    void enablesDiagnosticsOnlyWhenTheJvmPropertyIsTrue() {
        String property = "test.monitor.agent.debug";
        String previous = System.getProperty(property);
        try {
            System.clearProperty(property);
            assertFalse(AgentDiagnostics.enabled());
            System.setProperty(property, "true");
            assertTrue(AgentDiagnostics.enabled());
        } finally {
            if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
        }
    }
}
