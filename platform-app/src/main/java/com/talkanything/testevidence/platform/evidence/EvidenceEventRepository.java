package com.talkanything.testevidence.platform.evidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceEventRepository extends JpaRepository<EvidenceEvent, UUID> {
    List<EvidenceEvent> findByTestRunIdOrderByEventTimeAscIdAsc(UUID testRunId);
    Optional<EvidenceEvent> findFirstByTestRunIdOrderByReceivedAtDescIdDesc(UUID testRunId);
    Optional<EvidenceEvent> findByTestRunIdAndSpanId(UUID testRunId, String spanId);
    @Query("select count(distinct event.traceId) from EvidenceEvent event where event.testRun.id = :runId")
    long countDistinctTraceIdsByTestRunId(@Param("runId") UUID runId);
    void deleteByTestRun_Id(UUID runId);
}
