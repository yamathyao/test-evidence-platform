package com.talkanything.testevidence.agent.dubbo;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import java.time.Instant;

public final class DubboEvidence implements EvidencePayload {
    private final TestContext context;
    private final String serviceName;
    private final String direction;
    private final String target;
    private final Instant eventTime;
    private final long durationMillis;
    private final String errorSummary;
    private final Payload payload;

    public DubboEvidence(TestContext context, String serviceName, String direction, String target,
                         Instant eventTime, long durationMillis, String errorSummary) {
        this(context, serviceName, direction, target, eventTime, durationMillis, errorSummary, null);
    }

    public DubboEvidence(TestContext context, String serviceName, String direction, String target,
                         Instant eventTime, long durationMillis, String errorSummary, Payload payload) {
        this.context = context;
        this.serviceName = limit(serviceName, 160);
        this.direction = limit(direction, 32);
        this.target = limit(target, 1000);
        this.eventTime = eventTime;
        this.durationMillis = Math.max(0, durationMillis);
        this.errorSummary = limit(errorSummary, 1000);
        this.payload = payload;
    }

    @Override public String runId() { return context.runId(); }
    @Override public String profileId() { return context.profileId(); }
    @Override public int profileVersion() { return context.profileVersion(); }
    @Override public String serviceName() { return serviceName; }

    @Override
    public String toJson() {
        return "{\"testRunId\":\"" + q(context.runId()) + "\",\"profileId\":\"" + q(context.profileId())
                + "\",\"profileVersion\":" + context.profileVersion() + ",\"traceId\":\"" + q(context.traceId())
                + "\",\"spanId\":\"" + q(context.spanId()) + "\",\"parentSpanId\":\"" + q(context.parentSpanId())
                + "\",\"serviceName\":\"" + q(serviceName) + "\",\"protocol\":\"DUBBO\",\"direction\":\""
                + q(direction) + "\",\"httpMethod\":null,\"target\":\"" + q(target) + "\",\"statusCode\":null"
                + ",\"eventTime\":\"" + eventTime.toString() + "\",\"durationMillis\":" + durationMillis
                + ",\"errorSummary\":\"" + q(errorSummary)
                + "\",\"jdbcOperation\":null,\"sqlTemplate\":null,\"jdbcParameters\":null"
                + (payload == null ? "" : ",\"protocolPayload\":" + payload.toJson()) + "}";
    }

    private static String q(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    private static String limit(String value, int length) { return value == null ? "" : value.substring(0, Math.min(length, value.length())); }

    public static final class Payload {
        private final DubboPayloadCapture.Captured request;
        private final DubboPayloadCapture.Captured response;

        Payload(DubboPayloadCapture.Captured request, DubboPayloadCapture.Captured response) {
            this.request = request;
            this.response = response;
        }

        private String toJson() {
            return "{\"requestContentType\":\"" + q(request.contentType()) + "\",\"responseContentType\":\""
                    + q(response.contentType()) + "\",\"requestStatus\":\"" + q(request.status())
                    + "\",\"responseStatus\":\"" + q(response.status()) + "\",\"requestBody\":"
                    + nullable(request.body()) + ",\"responseBody\":" + nullable(response.body())
                    + ",\"requestTruncated\":" + request.truncated() + ",\"responseTruncated\":"
                    + response.truncated() + "}";
        }

        private static String nullable(String value) { return value == null ? "null" : "\"" + q(value) + "\""; }
    }
}
