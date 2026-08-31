package com.talkanything.testevidence.platform.evidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentDeliveryDiagnosticRepository extends JpaRepository<AgentDeliveryDiagnostic, UUID> {
    Optional<AgentDeliveryDiagnostic> findByTestRunIdAndServiceName(UUID testRunId, String serviceName);
    List<AgentDeliveryDiagnostic> findByTestRunIdOrderByServiceNameAsc(UUID testRunId);
    void deleteByTestRun_Id(UUID runId);
}
