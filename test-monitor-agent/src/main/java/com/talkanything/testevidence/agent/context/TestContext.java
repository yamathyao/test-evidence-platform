package com.talkanything.testevidence.agent.context;

import java.util.UUID;

public final class TestContext {
    private final String runId;
    private final String caseId;
    private final String profileId;
    private final int profileVersion;
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final boolean payloadCaptureEnabled;

    public TestContext(String runId, String caseId, String profileId, int profileVersion, String traceId,
                       String spanId, String parentSpanId) {
        this(runId, caseId, profileId, profileVersion, traceId, spanId, parentSpanId, false);
    }

    public TestContext(String runId, String caseId, String profileId, int profileVersion, String traceId,
                       String spanId, String parentSpanId, boolean payloadCaptureEnabled) {
        this.runId = runId;
        this.caseId = caseId;
        this.profileId = profileId;
        this.profileVersion = profileVersion;
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.payloadCaptureEnabled = payloadCaptureEnabled;
    }

    static TestContext root(String runId, String caseId, String profileId, int profileVersion) {
        return root(runId, caseId, profileId, profileVersion, false);
    }
    static TestContext root(String runId, String caseId, String profileId, int profileVersion, boolean payloadCaptureEnabled) {
        return new TestContext(runId, caseId, profileId, profileVersion, id(), id(), "", payloadCaptureEnabled);
    }

    static TestContext inherited(String runId, String caseId, String profileId, int profileVersion,
                                 String traceId, String parentSpanId) {
        return inherited(runId, caseId, profileId, profileVersion, traceId, parentSpanId, false);
    }
    static TestContext inherited(String runId, String caseId, String profileId, int profileVersion,
                                 String traceId, String parentSpanId, boolean payloadCaptureEnabled) {
        return new TestContext(runId, caseId, profileId, profileVersion, traceId, id(), parentSpanId, payloadCaptureEnabled);
    }

    TestContext child() { return new TestContext(runId, caseId, profileId, profileVersion, traceId, id(), spanId, payloadCaptureEnabled); }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    public String runId() { return runId; }
    public String caseId() { return caseId; }
    public String profileId() { return profileId; }
    public int profileVersion() { return profileVersion; }
    public String traceId() { return traceId; }
    public String spanId() { return spanId; }
    public String parentSpanId() { return parentSpanId; }
    public boolean payloadCaptureEnabled() { return payloadCaptureEnabled; }
}
