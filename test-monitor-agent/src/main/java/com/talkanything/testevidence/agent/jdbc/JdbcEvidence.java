package com.talkanything.testevidence.agent.jdbc;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class JdbcEvidence implements EvidencePayload {
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern LARGE_NUMBER = Pattern.compile("\\b\\d{6,}\\b");
    private final TestContext context;
    private final String serviceName;
    private final String operation;
    private final String sqlTemplate;
    private final List<JdbcValueSummary> parameters;
    private final Instant eventTime;
    private final long durationMillis;
    private final String errorSummary;

    public JdbcEvidence(TestContext context, String serviceName, String operation, String sqlTemplate,
                        List<JdbcValueSummary> parameters, Instant eventTime, long durationMillis, String errorSummary) {
        this.context = context;
        this.serviceName = limit(serviceName, 160);
        this.operation = limit(operation, 16);
        this.sqlTemplate = limit(sqlTemplate, 4000);
        this.parameters = limitParameters(parameters);
        this.eventTime = eventTime;
        this.durationMillis = Math.max(0, durationMillis);
        this.errorSummary = limit(errorSummary, 1000);
    }

    public static String maskStatementSql(String sql) {
        if (sql == null) return "";
        return LARGE_NUMBER.matcher(STRING_LITERAL.matcher(sql).replaceAll("'?'")).replaceAll("?");
    }

    @Override
    public String toJson() {
        return "{\"testRunId\":\"" + q(context.runId()) + "\",\"profileId\":\"" + q(context.profileId())
                + "\",\"profileVersion\":" + context.profileVersion() + ",\"traceId\":\"" + q(context.traceId())
                + "\",\"spanId\":\"" + q(context.spanId()) + "\",\"parentSpanId\":\"" + q(context.parentSpanId())
                + "\",\"serviceName\":\"" + q(serviceName) + "\",\"protocol\":\"JDBC\",\"direction\":\"CLIENT\""
                + ",\"httpMethod\":null,\"target\":\"mysql\",\"statusCode\":null,\"eventTime\":\"" + eventTime.toString()
                + "\",\"durationMillis\":" + durationMillis + ",\"errorSummary\":\"" + q(errorSummary)
                + "\",\"jdbcOperation\":\"" + q(operation) + "\",\"sqlTemplate\":\"" + q(sqlTemplate)
                + "\",\"jdbcParameters\":" + parametersJson() + "}";
    }

    private String parametersJson() {
        StringBuilder value = new StringBuilder("[");
        for (int index = 0; index < parameters.size(); index++) {
            if (index > 0) value.append(',');
            JdbcValueSummary parameter = parameters.get(index);
            value.append("{\"index\":").append(parameter.index()).append(",\"setter\":\"")
                    .append(q(parameter.setter())).append("\",\"valueLength\":").append(parameter.valueLength())
                    .append(",\"sha256\":\"").append(parameter.sha256()).append("\"}");
        }
        return value.append(']').toString();
    }

    private static List<JdbcValueSummary> limitParameters(List<JdbcValueSummary> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<JdbcValueSummary>(values.subList(0, Math.min(values.size(), 100))));
    }

    private static String q(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
    private static String limit(String value, int length) { if (value == null) return ""; return value.substring(0, Math.min(length, value.length())); }
}
