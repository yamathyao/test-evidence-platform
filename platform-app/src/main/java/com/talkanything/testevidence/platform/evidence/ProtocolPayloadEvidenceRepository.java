package com.talkanything.testevidence.platform.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface ProtocolPayloadEvidenceRepository extends JpaRepository<ProtocolPayloadEvidence, UUID> {
    Optional<ProtocolPayloadEvidence> findByEvidenceEvent_Id(UUID evidenceEventId);
    List<ProtocolPayloadEvidence> findByEvidenceEvent_TestRun_TestCase_IdAndExpiresAtAfter(UUID caseId, Instant now);
    @Modifying int deleteByExpiresAtLessThanEqual(Instant now);
    @Modifying void deleteByEvidenceEvent_TestRun_Id(UUID runId);
}
