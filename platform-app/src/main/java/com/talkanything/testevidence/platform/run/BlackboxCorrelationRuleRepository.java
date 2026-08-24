package com.talkanything.testevidence.platform.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BlackboxCorrelationRuleRepository extends JpaRepository<BlackboxCorrelationRule, UUID> {
    List<BlackboxCorrelationRule> findByStatusAndExpiresAtAfter(BlackboxCorrelationRuleStatus status, Instant now);
    List<BlackboxCorrelationRule> findByTestRun_IdAndStatus(UUID runId, BlackboxCorrelationRuleStatus status);
    long deleteByTestRun_IdAndStatus(UUID runId, BlackboxCorrelationRuleStatus status);
    long deleteByExpiresAtBefore(Instant now);

    @Modifying
    @Query("update BlackboxCorrelationRule rule set rule.status = :bound, rule.boundAt = :now, rule.expiresAt = :expiresAt "
            + "where rule.testRun.id = :runId and rule.status = :active")
    int bindActive(@Param("runId") UUID runId, @Param("active") BlackboxCorrelationRuleStatus active,
                   @Param("bound") BlackboxCorrelationRuleStatus bound, @Param("now") Instant now,
                   @Param("expiresAt") Instant expiresAt);
}
