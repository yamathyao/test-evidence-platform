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

    public DubboEvidence(TestContext context, String serviceName, String direction, String target,
                         Instant eventTime, long durationMillis, String errorSummary) {
        this.context = context;
        this.serviceName = limit(serviceName, 160);
        this.direction = limit(direction, 32);
        this.target = limit(target, 1000);
        this.eventTime = eventTime;
        this.durationMillis = Math.max(0, durationMillis);
        this.errorSummary = limit(errorSummary, 1000);
    }

    @Override
    public String toJson() {
        return "{\"testRunId\":\"" + q(context.runId()) + "\",\"profileId\":\"" + q(context.profileId())
                + "\",\"profileVersion\":" + context.profileVersion() + ",\"traceId\":\"" + q(context.traceId())
                + "\",\"spanId\":\"" + q(context.spanId()) + "\",\"parentSpanId\":\"" + q(context.parentSpanId())
                + "\",\"serviceName\":\"" + q(serviceName) + "\",\"protocol\":\"DUBBO\",\"direction\":\""
                + q(direction) + "\",\"httpMethod\":null,\"target\":\"" + q(target) + "\",\"statusCode\":null"
                + ",\"eventTime\":\"" + eventTime.toString() + "\",\"durationMillis\":" + durationMillis
                + ",\"errorSummary\":\"" + q(errorSummary)
                + "\",\"jdbcOperation\":null,\"sqlTemplate\":null,\"jdbcParameters\":null}";
    }

    private static String q(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    private static String limit(String value, int length) { return value == null ? "" : value.substring(0, Math.min(length, value.length())); }
}
