package com.talkanything.testevidence.platform.evidence;

import com.talkanything.testevidence.platform.run.TestRun;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "agent_delivery_diagnostic", uniqueConstraints = @UniqueConstraint(columnNames = {"test_run_id", "service_name"}))
public class AgentDeliveryDiagnostic {
    @Id private UUID id;
    @ManyToOne @JoinColumn(name = "test_run_id", nullable = false) private TestRun testRun;
    @Column(name = "profile_id", nullable = false) private UUID profileId;
    @Column(name = "profile_version", nullable = false) private int profileVersion;
    @Column(name = "service_name", nullable = false) private String serviceName;
    @Column(name = "dropped_evidence_count", nullable = false) private long droppedEvidenceCount;
    @Column(name = "delivery_failure_count", nullable = false) private long deliveryFailureCount;
    @Column(name = "last_failure") private String lastFailure;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AgentDeliveryDiagnostic() { }
    AgentDeliveryDiagnostic(TestRun testRun, UUID profileId, int profileVersion, String serviceName,
                            long droppedEvidenceCount, long deliveryFailureCount, String lastFailure) {
        this.id = UUID.randomUUID(); this.testRun = testRun; this.profileId = profileId;
        this.profileVersion = profileVersion; this.serviceName = serviceName;
        apply(droppedEvidenceCount, deliveryFailureCount, lastFailure);
    }

    void apply(long droppedEvidenceCount, long deliveryFailureCount, String lastFailure) {
        this.droppedEvidenceCount = Math.max(this.droppedEvidenceCount, droppedEvidenceCount);
        this.deliveryFailureCount = Math.max(this.deliveryFailureCount, deliveryFailureCount);
        if (lastFailure != null && !lastFailure.isBlank()) this.lastFailure = lastFailure;
        this.updatedAt = Instant.now();
    }

    public String getServiceName() { return serviceName; }
    public long getDroppedEvidenceCount() { return droppedEvidenceCount; }
    public long getDeliveryFailureCount() { return deliveryFailureCount; }
    public String getLastFailure() { return lastFailure; }
    public Instant getUpdatedAt() { return updatedAt; }
}
