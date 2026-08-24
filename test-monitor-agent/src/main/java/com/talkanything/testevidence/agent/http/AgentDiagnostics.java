package com.talkanything.testevidence.agent.http;

public final class AgentDiagnostics {
    private static final String PROPERTY = "test.monitor.agent.debug";

    private AgentDiagnostics() { }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void log(String message) {
        if (enabled()) System.err.println("[test-monitor-agent] " + message);
    }
}
