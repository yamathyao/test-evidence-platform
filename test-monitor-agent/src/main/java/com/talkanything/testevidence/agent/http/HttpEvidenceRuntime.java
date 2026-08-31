package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.time.Instant;

public final class HttpEvidenceRuntime {
    private static volatile AsyncEvidenceReporter reporter;
    private static volatile String serviceName;
    private static final HttpPayloadCaptureRuntime PAYLOADS = new HttpPayloadCaptureRuntime();
    private HttpEvidenceRuntime() { }
    public static void initialize(String service, AsyncEvidenceReporter value) { serviceName = service; reporter = value; }
    public static TestContext enter(String runId, String caseId, String profileId, String profileVersion) {
        return enter(runId, caseId, profileId, profileVersion, null, null);
    }
    public static TestContext enter(String runId, String caseId, String profileId, String profileVersion,
                                    String traceId, String parentSpanId) {
        return enter(runId, caseId, profileId, profileVersion, traceId, parentSpanId, false);
    }
    public static TestContext enter(String runId, String caseId, String profileId, String profileVersion,
                                    String traceId, String parentSpanId, boolean payloadCaptureEnabled) {
        if (blank(runId) || blank(caseId) || blank(profileId)) return null;
        try { return TestContextHolder.enter(runId, caseId, profileId, Integer.parseInt(profileVersion), traceId, parentSpanId, payloadCaptureEnabled); }
        catch (RuntimeException ignored) { return null; }
    }
    public static void complete(TestContext context, String method, String target, Integer status, long startedAt, Throwable failure) {
        complete(context, method, target, status, startedAt, failure, null, null);
    }
    public static void complete(TestContext context, String method, String target, Integer status, long startedAt, Throwable failure,
                                String responseContentType, String responseContentEncoding) {
        if (context == null) {
            PAYLOADS.finish(null, null);
            return;
        }
        try {
            AsyncEvidenceReporter value = reporter;
            HttpPayloadCaptureRuntime.HttpPayload payload = PAYLOADS.finish(responseContentType, responseContentEncoding);
            AgentDiagnostics.log("http.payload.evidence enabled=" + context.payloadCaptureEnabled()
                    + " attached=" + (payload != null));
            if (value != null) value.report(new HttpEvidence(context, serviceName, "SERVER", method, target, status,
                    Instant.ofEpochMilli(startedAt), Math.max(0, System.currentTimeMillis() - startedAt), summary(failure), payload));
        } catch (Throwable ignored) { } finally { TestContextHolder.clear(); }
    }
    public static void startPayload(TestContext context, String contentType, String contentEncoding) {
        PAYLOADS.start(context, contentType, contentEncoding);
    }
    public static void startPayload(TestContext context, String method, String contentType, String contentEncoding) {
        PAYLOADS.start(context, method, contentType, contentEncoding);
    }
    public static void startDeferredPayload(String contentType, String contentEncoding) {
        PAYLOADS.startDeferred(contentType, contentEncoding);
    }
    public static void activatePayload(TestContext context) {
        PAYLOADS.activate(context);
    }
    public static void requestBytes(byte[] bytes, int offset, int length) { PAYLOADS.request(bytes, offset, length); }
    public static void responseBytes(byte[] bytes, int offset, int length) { PAYLOADS.response(bytes, offset, length); }
    private static boolean blank(String value) { return value == null || value.isEmpty(); }
    private static String summary(Throwable failure) { return failure == null ? "" : failure.getClass().getSimpleName(); }
}
