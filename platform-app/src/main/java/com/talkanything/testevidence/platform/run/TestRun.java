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
@Table(name = "test_run")
public class TestRun {
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "test_case_id", nullable = false) private TestCase testCase;
    @Column(name = "profile_version", nullable = false) private int profileVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RunStatus status;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "failure_reason") private String failureReason;
    @Column(name = "draining_at") private Instant drainingAt;
    @Enumerated(EnumType.STRING) @Column(name = "finish_reason") private TestRunFinishReason finishReason;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "case_snapshot_json", columnDefinition = "jsonb") private String caseSnapshotJson;

    protected TestRun() { }
    TestRun(TestCase testCase, String caseSnapshotJson) {
        this.id = UUID.randomUUID(); this.testCase = testCase;
        this.profileVersion = testCase.getProfile().getVersion(); this.status = RunStatus.QUEUED;
        this.caseSnapshotJson = caseSnapshotJson;
    }
    void start() { this.status = RunStatus.RUNNING; this.startedAt = Instant.now(); }
    void beginDraining(TestRunFinishReason reason) {
        beginDraining(reason, Instant.now());
    }
    void beginDraining(TestRunFinishReason reason, Instant at) {
        if (status != RunStatus.RUNNING) throw new IllegalStateException("Run is not collecting");
        this.status = RunStatus.DRAINING;
        this.drainingAt = at;
        this.finishReason = reason;
    }
    void finishDraining() {
        if (status != RunStatus.DRAINING) throw new IllegalStateException("Run is not draining");
        if (finishReason == TestRunFinishReason.TIMEOUT) fail("Blackbox capture window timed out");
        else succeed();
    }
    void succeed() { this.status = RunStatus.SUCCEEDED; this.finishedAt = Instant.now(); }
    void fail(String reason) { this.status = RunStatus.FAILED; this.failureReason = reason; this.finishedAt = Instant.now(); }
    public UUID getId() { return id; }
    TestCase getTestCase() { return testCase; }
    public UUID getProfileId() { return testCase.getProfile().getId(); }
    public int getProfileVersion() { return profileVersion; }
    public RunStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public java.time.Instant getStartedAt() { return startedAt; }
    public java.time.Instant getFinishedAt() { return finishedAt; }
    public Instant getDrainingAt() { return drainingAt; }
    public TestRunFinishReason getFinishReason() { return finishReason; }
    public String getCaseSnapshotJson() { return caseSnapshotJson; }
    public Instant getTestCaseCompletedAt() { return testCase.getCompletedAt(); }
}
