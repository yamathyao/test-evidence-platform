package com.talkanything.testevidence.agent.context;

import java.util.Optional;

public final class TestContextHolder {
    private static final ThreadLocal<TestContext> CURRENT = new ThreadLocal<TestContext>();
    private TestContextHolder() { }
    public static TestContext enter(String runId, String caseId, String profileId, int profileVersion) {
        return enter(runId, caseId, profileId, profileVersion, false);
    }
    public static TestContext enter(String runId, String caseId, String profileId, int profileVersion, boolean payloadCaptureEnabled) {
        TestContext context = TestContext.root(runId, caseId, profileId, profileVersion, payloadCaptureEnabled);
        CURRENT.set(context);
        return context;
    }
    public static TestContext enter(String runId, String caseId, String profileId, int profileVersion,
                                    String traceId, String parentSpanId) {
        return enter(runId, caseId, profileId, profileVersion, traceId, parentSpanId, false);
    }
    public static TestContext enter(String runId, String caseId, String profileId, int profileVersion,
                                    String traceId, String parentSpanId, boolean payloadCaptureEnabled) {
        TestContext context = valid(traceId) && valid(parentSpanId)
                ? TestContext.inherited(runId, caseId, profileId, profileVersion, traceId, parentSpanId, payloadCaptureEnabled)
                : TestContext.root(runId, caseId, profileId, profileVersion, payloadCaptureEnabled);
        CURRENT.set(context);
        return context;
    }
    public static TestContext child() { return CURRENT.get().child(); }
    public static Optional<TestContext> current() { return Optional.ofNullable(CURRENT.get()); }
    public static TestContext currentOrNull() { return CURRENT.get(); }
    public static void restore(TestContext context) {
        if (context == null) CURRENT.remove(); else CURRENT.set(context);
    }
    public static void clear() { CURRENT.remove(); }
    private static boolean valid(String value) { return value != null && !value.isEmpty(); }
}
