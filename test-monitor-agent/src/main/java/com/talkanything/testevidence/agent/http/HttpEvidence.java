package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import java.time.Instant;

public final class HttpEvidence implements EvidencePayload {
    private final TestContext context;
    private final String serviceName;
    private final String direction;
    private final String method;
    private final String target;
    private final Integer statusCode;
    private final Instant eventTime;
    private final long durationMillis;
    private final String errorSummary;
    private final HttpPayloadCaptureRuntime.HttpPayload payload;

    public HttpEvidence(TestContext context, String serviceName, String direction, String method, String target,
                        Integer statusCode, Instant eventTime, long durationMillis, String errorSummary) {
        this(context, serviceName, direction, method, target, statusCode, eventTime, durationMillis, errorSummary, null);
    }
    public HttpEvidence(TestContext context, String serviceName, String direction, String method, String target,
                        Integer statusCode, Instant eventTime, long durationMillis, String errorSummary,
                        HttpPayloadCaptureRuntime.HttpPayload payload) {
        this.context = context; this.serviceName = limit(serviceName, 160); this.direction = direction;
        this.method = limit(method, 16); this.target = limit(target, 1000); this.statusCode = statusCode;
        this.eventTime = eventTime; this.durationMillis = Math.max(0, durationMillis); this.errorSummary = limit(errorSummary, 1000);
        this.payload = payload;
    }

    static HttpEvidence sample(String spanId) {
        TestContext context = new TestContext("00000000-0000-0000-0000-000000000001", "case", "00000000-0000-0000-0000-000000000002", 1,
                "trace", spanId, "");
        return new HttpEvidence(context, "test", "SERVER", "GET", "/", 200, Instant.now(), 0, "");
    }

    @Override
    public String toJson() {
        return "{\"testRunId\":\"" + q(context.runId()) + "\",\"profileId\":\"" + q(context.profileId())
                + "\",\"profileVersion\":" + context.profileVersion() + ",\"traceId\":\"" + q(context.traceId())
                + "\",\"spanId\":\"" + q(context.spanId()) + "\",\"parentSpanId\":\"" + q(context.parentSpanId())
                + "\",\"serviceName\":\"" + q(serviceName) + "\",\"protocol\":\"HTTP\",\"direction\":\"" + q(direction)
                + "\",\"httpMethod\":\"" + q(method) + "\",\"target\":\"" + q(target) + "\",\"statusCode\":"
                + (statusCode == null ? "null" : statusCode) + ",\"eventTime\":\"" + eventTime.toString()
                + "\",\"durationMillis\":" + durationMillis + ",\"errorSummary\":\"" + q(errorSummary) + "\""
                + (payload == null ? "" : ",\"httpPayload\":" + payloadJson()) + "}";
    }
    private String payloadJson() {
        return "{\"requestContentType\":\"" + q(payload.requestContentType()) + "\",\"responseContentType\":\""
                + q(payload.responseContentType()) + "\",\"requestStatus\":\"" + q(payload.requestStatus())
                + "\",\"responseStatus\":\"" + q(payload.responseStatus()) + "\",\"requestBody\":" + nullable(payload.requestBody())
                + ",\"responseBody\":" + nullable(payload.responseBody()) + ",\"requestTruncated\":" + payload.requestTruncated()
                + ",\"responseTruncated\":" + payload.responseTruncated() + "}";
    }
    private static String nullable(String value) { return value == null ? "null" : "\"" + q(value) + "\""; }
    private static String q(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    private static String limit(String value, int length) { if (value == null) return ""; return value.substring(0, Math.min(length, value.length())); }
}
