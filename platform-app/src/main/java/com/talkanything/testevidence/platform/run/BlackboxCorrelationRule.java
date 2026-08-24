package com.talkanything.testevidence.platform.run;

import com.talkanything.testevidence.platform.casefile.TestCase;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "blackbox_correlation_rule")
public class BlackboxCorrelationRule {
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "test_run_id", nullable = false) private TestRun testRun;
    @ManyToOne @JoinColumn(name = "test_case_id", nullable = false) private TestCase testCase;
    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(name = "profile_version", nullable = false) private int profileVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "target_services", nullable = false, columnDefinition = "jsonb")
    private String targetServicesJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    private String definitionJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "value_json", nullable = false, columnDefinition = "jsonb")
    private String valueJson;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private BlackboxCorrelationRuleStatus status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "bound_at") private Instant boundAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected BlackboxCorrelationRule() { }

    BlackboxCorrelationRule(TestRun testRun, String targetServicesJson, String definitionJson, String valueJson,
                            Instant expiresAt) {
        this.id = UUID.randomUUID(); this.testRun = testRun; this.testCase = testRun.getTestCase();
        this.profileId = testRun.getProfileId(); this.profileVersion = testRun.getProfileVersion();
        this.targetServicesJson = targetServicesJson; this.definitionJson = definitionJson; this.valueJson = valueJson;
        this.status = BlackboxCorrelationRuleStatus.ACTIVE; this.expiresAt = expiresAt; this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTestRunId() { return testRun.getId(); }
    public UUID getTestCaseId() { return testCase.getId(); }
    public UUID getProfileId() { return profileId; }
    public int getProfileVersion() { return profileVersion; }
    public String getTargetServicesJson() { return targetServicesJson; }
    public String getDefinitionJson() { return definitionJson; }
    public String getValueJson() { return valueJson; }
    public BlackboxCorrelationRuleStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isHttpPayloadCaptureEnabled() { return testCase.isHttpPayloadCaptureEnabled(); }
}
