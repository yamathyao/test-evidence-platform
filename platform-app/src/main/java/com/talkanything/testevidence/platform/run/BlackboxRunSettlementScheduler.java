package com.talkanything.testevidence.platform.run;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class BlackboxRunSettlementScheduler {
    private final TestRunService runService;

    BlackboxRunSettlementScheduler(TestRunService runService) {
        this.runService = runService;
    }

    @Scheduled(fixedDelayString = "${test-evidence.blackbox-settlement-fixed-delay-ms:1000}")
    void settle() {
        runService.settleBlackboxRuns(Instant.now());
    }
}
