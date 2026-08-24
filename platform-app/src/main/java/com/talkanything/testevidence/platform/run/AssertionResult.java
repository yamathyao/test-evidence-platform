package com.talkanything.testevidence.platform.run;

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
@Table(name = "assertion_result")
public class AssertionResult {
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "test_run_id", nullable = false) private TestRun testRun;
    @Column(name = "sequence_no", nullable = false) private int sequenceNo;
    @Column(name = "assertion_type", nullable = false) private String assertionType;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AssertionResultStatus status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "expected_json", nullable = false, columnDefinition = "jsonb") private String expectedJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "actual_json", nullable = false, columnDefinition = "jsonb") private String actualJson;
    @Column(name = "failure_reason") private String failureReason;

    protected AssertionResult() { }

    AssertionResult(TestRun testRun, int sequenceNo, String assertionType, AssertionResultStatus status,
                    String expectedJson, String actualJson, String failureReason) {
        this.id = UUID.randomUUID(); this.testRun = testRun; this.sequenceNo = sequenceNo;
        this.assertionType = assertionType; this.status = status; this.expectedJson = expectedJson;
        this.actualJson = actualJson; this.failureReason = failureReason;
    }

    public int getSequenceNo() { return sequenceNo; }
    public String getAssertionType() { return assertionType; }
    public AssertionResultStatus getStatus() { return status; }
    public String getExpectedJson() { return expectedJson; }
    public String getActualJson() { return actualJson; }
    public String getFailureReason() { return failureReason; }
}
