package com.talkanything.testevidence.agent.dubbo;

import com.talkanything.testevidence.agent.context.TestContext;
import java.util.Map;

public final class DubboContextPropagation {
    private static final String RUN_ID = "x-test-run-id";
    private static final String CASE_ID = "x-test-case-id";
    private static final String PROFILE_ID = "x-test-profile-id";
    private static final String PROFILE_VERSION = "x-test-profile-version";
    private static final String TRACE_ID = "x-test-trace-id";
    private static final String PARENT_SPAN_ID = "x-test-parent-span-id";
    private static final String PAYLOAD_CAPTURE = "x-test-capture-protocol-payload";

    private DubboContextPropagation() { }

    public static boolean inject(Map<String, String> attachments, TestContext context) {
        if (attachments == null || context == null || conflicts(attachments, context)) return false;
        putIfAbsent(attachments, RUN_ID, context.runId());
        putIfAbsent(attachments, CASE_ID, context.caseId());
        putIfAbsent(attachments, PROFILE_ID, context.profileId());
        putIfAbsent(attachments, PROFILE_VERSION, Integer.toString(context.profileVersion()));
        putIfAbsent(attachments, TRACE_ID, context.traceId());
        putIfAbsent(attachments, PARENT_SPAN_ID, context.spanId());
        putIfAbsent(attachments, PAYLOAD_CAPTURE, Boolean.toString(context.payloadCaptureEnabled()));
        return true;
    }

    public static Metadata read(Map<String, String> attachments) {
        if (attachments == null) return null;
        String runId = attachments.get(RUN_ID);
        String caseId = attachments.get(CASE_ID);
        String profileId = attachments.get(PROFILE_ID);
        String version = attachments.get(PROFILE_VERSION);
        String traceId = attachments.get(TRACE_ID);
        String parentSpanId = attachments.get(PARENT_SPAN_ID);
        boolean payloadCaptureEnabled = "true".equals(attachments.get(PAYLOAD_CAPTURE));
        if (blank(runId) || blank(caseId) || blank(profileId) || blank(version) || blank(traceId) || blank(parentSpanId)) return null;
        try {
            int profileVersion = Integer.parseInt(version);
            return profileVersion > 0 ? new Metadata(runId, caseId, profileId, profileVersion, traceId, parentSpanId,
                    payloadCaptureEnabled) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    private static boolean conflicts(Map<String, String> attachments, TestContext context) {
        return conflicts(attachments, RUN_ID, context.runId()) || conflicts(attachments, CASE_ID, context.caseId())
                || conflicts(attachments, PROFILE_ID, context.profileId())
                || conflicts(attachments, PROFILE_VERSION, Integer.toString(context.profileVersion()))
                || conflicts(attachments, TRACE_ID, context.traceId())
                || conflicts(attachments, PARENT_SPAN_ID, context.spanId());
    }

    private static boolean conflicts(Map<String, String> attachments, String key, String value) {
        String existing = attachments.get(key);
        return existing != null && !existing.equals(value);
    }

    private static void putIfAbsent(Map<String, String> attachments, String key, String value) {
        if (!attachments.containsKey(key)) attachments.put(key, value);
    }

    private static boolean blank(String value) { return value == null || value.isEmpty(); }

    public static final class Metadata {
        private final String runId;
        private final String caseId;
        private final String profileId;
        private final int profileVersion;
        private final String traceId;
        private final String parentSpanId;
        private final boolean payloadCaptureEnabled;

        public Metadata(String runId, String caseId, String profileId, int profileVersion, String traceId, String parentSpanId,
                        boolean payloadCaptureEnabled) {
            this.runId = runId;
            this.caseId = caseId;
            this.profileId = profileId;
            this.profileVersion = profileVersion;
            this.traceId = traceId;
            this.parentSpanId = parentSpanId;
            this.payloadCaptureEnabled = payloadCaptureEnabled;
        }

        public String runId() { return runId; }
        public String caseId() { return caseId; }
        public String profileId() { return profileId; }
        public int profileVersion() { return profileVersion; }
        public String traceId() { return traceId; }
        public String parentSpanId() { return parentSpanId; }
        public boolean payloadCaptureEnabled() { return payloadCaptureEnabled; }
    }
}
