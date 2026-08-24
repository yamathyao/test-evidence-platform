package com.talkanything.testevidence.platform.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.talkanything.testevidence.platform.run.TestRun;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "evidence_event")
public class EvidenceEvent {
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "test_run_id", nullable = false) private TestRun testRun;
    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(name = "profile_version", nullable = false) private int profileVersion;
    @Column(name = "trace_id", nullable = false) private String traceId;
    @Column(name = "span_id", nullable = false) private String spanId;
    @Column(name = "parent_span_id") private String parentSpanId;
    @Column(name = "service_name", nullable = false) private String serviceName;
    @Column(nullable = false) private String protocol;
    @Column(nullable = false) private String direction;
    @Column(name = "http_method") private String httpMethod;
    @Column(nullable = false) private String target;
    @Column(name = "status_code") private Integer statusCode;
    @Column(name = "event_time", nullable = false) private Instant eventTime;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;
    @Column(name = "duration_millis", nullable = false) private long durationMillis;
    @Column(name = "error_summary") private String errorSummary;
    @Column(name = "jdbc_operation") private String jdbcOperation;
    @Column(name = "sql_template") private String sqlTemplate;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "jdbc_parameters") private JsonNode jdbcParameters;

    protected EvidenceEvent() { }

    public EvidenceEvent(TestRun testRun, UUID profileId, int profileVersion, String traceId, String spanId,
                         String parentSpanId, String serviceName, String protocol, String direction,
                         String httpMethod, String target, Integer statusCode, Instant eventTime,
                         long durationMillis, String errorSummary, String jdbcOperation, String sqlTemplate,
                         JsonNode jdbcParameters) {
        this.id = UUID.randomUUID();
        this.testRun = testRun;
        this.profileId = profileId;
        this.profileVersion = profileVersion;
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.serviceName = serviceName;
        this.protocol = protocol;
        this.direction = direction;
        this.httpMethod = httpMethod;
        this.target = target;
        this.statusCode = statusCode;
        this.eventTime = eventTime;
        this.receivedAt = Instant.now();
        this.durationMillis = durationMillis;
        this.errorSummary = errorSummary;
        this.jdbcOperation = jdbcOperation;
        this.sqlTemplate = sqlTemplate;
        this.jdbcParameters = jdbcParameters;
    }

    public UUID getId() { return id; }
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getServiceName() { return serviceName; }
    public String getProtocol() { return protocol; }
    public String getDirection() { return direction; }
    public String getHttpMethod() { return httpMethod; }
    public String getTarget() { return target; }
    public Integer getStatusCode() { return statusCode; }
    public Instant getEventTime() { return eventTime; }
    public Instant getReceivedAt() { return receivedAt; }
    public long getDurationMillis() { return durationMillis; }
    public String getErrorSummary() { return errorSummary; }
    public String getJdbcOperation() { return jdbcOperation; }
    public String getSqlTemplate() { return sqlTemplate; }
    public JsonNode getJdbcParameters() { return jdbcParameters; }
}
