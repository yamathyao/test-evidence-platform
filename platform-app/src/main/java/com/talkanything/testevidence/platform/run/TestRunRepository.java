package com.talkanything.testevidence.platform.run;

import java.util.List;
import java.util.UUID;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

interface TestRunRepository extends JpaRepository<TestRun, UUID>, JpaSpecificationExecutor<TestRun> {
    @EntityGraph(attributePaths = "testCase")
    List<TestRun> findTop50ByOrderByStartedAtDesc();

    @Override
    @EntityGraph(attributePaths = "testCase")
    Page<TestRun> findAll(Specification<TestRun> specification, Pageable pageable);

    @EntityGraph(attributePaths = "testCase")
    List<TestRun> findByStatusAndTestCase_TriggerType(RunStatus status, TriggerType triggerType);
    List<TestRun> findByFinishedAtLessThanEqual(java.time.Instant cutoff);
}
