package com.talkanything.testevidence.platform.evidence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface HttpPayloadEvidenceRepository extends JpaRepository<HttpPayloadEvidence, UUID> {
    Optional<HttpPayloadEvidence> findByEvidenceEvent_Id(UUID evidenceEventId);
    java.util.List<HttpPayloadEvidence> findByEvidenceEvent_TestRun_TestCase_IdAndExpiresAtAfter(UUID caseId,
                                                                                                     java.time.Instant now);
    @Modifying int deleteByExpiresAtLessThanEqual(java.time.Instant now);
    @Modifying void deleteByEvidenceEvent_TestRun_Id(UUID runId);
}
