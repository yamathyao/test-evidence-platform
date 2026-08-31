package com.talkanything.testevidence.platform.run;

import com.talkanything.testevidence.platform.evidence.AgentDeliveryDiagnosticRepository;
import com.talkanything.testevidence.platform.evidence.EvidenceEventRepository;
import com.talkanything.testevidence.platform.evidence.HttpPayloadEvidenceRepository;
import com.talkanything.testevidence.platform.evidence.ProtocolPayloadEvidenceRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunRetentionService {
    private static final long RUN_RETENTION_SECONDS = 30L * 86400L;
    private final TestRunRepository runRepository;
    private final AssertionResultRepository assertionRepository;
    private final EvidenceEventRepository evidenceRepository;
    private final AgentDeliveryDiagnosticRepository diagnosticRepository;
    private final HttpPayloadEvidenceRepository payloadRepository;
    private final ProtocolPayloadEvidenceRepository protocolPayloadRepository;

    RunRetentionService(TestRunRepository runRepository, AssertionResultRepository assertionRepository,
                        EvidenceEventRepository evidenceRepository, HttpPayloadEvidenceRepository payloadRepository,
                        ProtocolPayloadEvidenceRepository protocolPayloadRepository, AgentDeliveryDiagnosticRepository diagnosticRepository) {
        this.runRepository = runRepository; this.assertionRepository = assertionRepository;
        this.evidenceRepository = evidenceRepository; this.payloadRepository = payloadRepository;
        this.protocolPayloadRepository = protocolPayloadRepository;
        this.diagnosticRepository = diagnosticRepository;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "${test-evidence.retention-zone:Asia/Shanghai}")
    public void scheduledCleanup() { cleanExpired(Instant.now()); }

    @Transactional
    public void cleanExpired(Instant now) {
        payloadRepository.deleteByExpiresAtLessThanEqual(now);
        protocolPayloadRepository.deleteByExpiresAtLessThanEqual(now);
        List<TestRun> expiredRuns = runRepository.findByFinishedAtLessThanEqual(now.minusSeconds(RUN_RETENTION_SECONDS));
        for (TestRun run : expiredRuns) deleteRun(run.getId());
    }

    private void deleteRun(java.util.UUID runId) {
        diagnosticRepository.deleteByTestRun_Id(runId);
        assertionRepository.deleteByTestRun_Id(runId);
        payloadRepository.deleteByEvidenceEvent_TestRun_Id(runId);
        protocolPayloadRepository.deleteByEvidenceEvent_TestRun_Id(runId);
        evidenceRepository.deleteByTestRun_Id(runId);
        runRepository.deleteById(runId);
    }
}
