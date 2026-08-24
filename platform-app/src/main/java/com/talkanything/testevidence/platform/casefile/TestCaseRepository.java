package com.talkanything.testevidence.platform.casefile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TestCaseRepository extends JpaRepository<TestCase, UUID>, JpaSpecificationExecutor<TestCase> {
    @EntityGraph(attributePaths = {"profile", "assertions"})
    Optional<TestCase> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"profile", "assertions"})
    List<TestCase> findTop50ByOrderByCreatedAtDesc();
}
