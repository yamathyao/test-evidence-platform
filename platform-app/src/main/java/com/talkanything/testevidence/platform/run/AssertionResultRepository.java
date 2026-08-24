package com.talkanything.testevidence.platform.run;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssertionResultRepository extends JpaRepository<AssertionResult, UUID> {
    List<AssertionResult> findByTestRunIdOrderBySequenceNoAsc(UUID testRunId);
    void deleteByTestRun_Id(UUID runId);
}
