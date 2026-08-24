package com.talkanything.testevidence.platform.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import com.talkanything.testevidence.platform.run.BlackboxCorrelationService;
import com.talkanything.testevidence.platform.run.HttpTriggerClient;
import com.talkanything.testevidence.platform.run.HttpTriggerResponse;
import com.talkanything.testevidence.platform.run.RunStatus;
import com.talkanything.testevidence.platform.run.TestRun;
import com.talkanything.testevidence.platform.run.TestRunFinishReason;
import com.talkanything.testevidence.platform.run.TestRunService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlackboxRunSettlementTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CaptureProfileService profileService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService runService;
    @Autowired private BlackboxCorrelationService correlationService;
    @Autowired private EvidenceEventRepository evidenceRepository;
    @MockBean private HttpTriggerClient triggerClient;

    @Test
    void recordsPlatformReceiptTimeForBlackboxEvidence() throws Exception {
        TestRun run = startBrowserRun(60);
        Instant before = Instant.now();

        ingest(run, "receipt");

        EvidenceEvent event = evidenceRepository.findFirstByTestRunIdOrderByReceivedAtDescIdDesc(run.getId()).orElseThrow();
        assertFalse(event.getReceivedAt().isBefore(before));
    }

    @Test
    void movesBrowserRunToDrainingWhenNoEvidenceArrivesBeforeTimeout() throws Exception {
        TestRun run = startBrowserRun(60);

        runService.settleBlackboxRuns(run.getStartedAt().plusSeconds(61));

        assertEquals(RunStatus.DRAINING, runService.find(run.getId()).getStatus());
        assertEquals(TestRunFinishReason.TIMEOUT, runService.find(run.getId()).getFinishReason());
        assertFalse(correlationService.hasActiveRule(run.getId()));
    }

    @Test
    void keepsBrowserRunCollectingAfterEvidenceBecomesQuiet() throws Exception {
        TestRun run = startBrowserRun(60);
        ingest(run, "quiet");
        EvidenceEvent event = evidenceRepository.findFirstByTestRunIdOrderByReceivedAtDescIdDesc(run.getId()).orElseThrow();

        runService.settleBlackboxRuns(event.getReceivedAt().plusSeconds(3));

        assertEquals(RunStatus.RUNNING, runService.find(run.getId()).getStatus());
    }

    @Test
    void manualCompletionStopsRulesThenSucceedsAfterDrainPeriod() throws Exception {
        TestRun run = startBrowserRun(60);

        runService.complete(run.getId());

        assertEquals(RunStatus.DRAINING, runService.find(run.getId()).getStatus());
        assertEquals(TestRunFinishReason.MANUAL, runService.find(run.getId()).getFinishReason());
        assertFalse(correlationService.hasActiveRule(run.getId()));
        runService.settleBlackboxRuns(run.getStartedAt().plusSeconds(11));
        assertEquals(RunStatus.SUCCEEDED, runService.find(run.getId()).getStatus());
    }

    @Test
    void leavesTerminalRunUnchangedWhenSettlementRunsAgain() throws Exception {
        TestRun run = startBrowserRun(60);

        runService.settleBlackboxRuns(run.getStartedAt().plusSeconds(61));
        runService.settleBlackboxRuns(run.getStartedAt().plusSeconds(121));

        assertEquals(RunStatus.FAILED, runService.find(run.getId()).getStatus());
    }

    @Test
    void leavesDirectHttpRunUnchangedDuringBlackboxSettlement() throws Exception {
        CaptureProfile profile = profileService.create("settlement-http-" + System.nanoTime(), 1, "{}");
        TestCase testCase = testCaseService.create(new TestCaseRequest("settlement http", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/settlement\"}"), 60, List.of()));
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(testCase);

        runService.settleBlackboxRuns(run.getStartedAt().plusSeconds(120));

        assertEquals(RunStatus.SUCCEEDED, runService.find(run.getId()).getStatus());
    }

    private TestRun startBrowserRun(int timeoutSeconds) throws Exception {
        CaptureProfile profile = profileService.create("settlement-profile-" + System.nanoTime(), 1,
                "{\"blackboxCorrelation\":{\"defaultTtlSeconds\":60,\"retentionSeconds\":300,\"singleUse\":true,"
                        + "\"targetServices\":[\"settlement-service\"],\"matchGroups\":[{\"name\":\"browser\","
                        + "\"matchers\":[{\"field\":\"orderNo\",\"locations\":[\"JSON_BODY\"],\"match\":\"EXACT\"}]}]}}");
        TestCase testCase = testCaseService.create(new TestCaseRequest("settlement browser", profile.getId(), TriggerType.BROWSER,
                objectMapper.readTree("{}"), timeoutSeconds, List.of()));
        return runService.startBlackbox(testCase, Map.of("orderNo", "settlement-order"), null);
    }

    private void ingest(TestRun run, String spanId) throws Exception {
        String event = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"settlement-trace\",\"spanId\":\"" + spanId
                + "\",\"parentSpanId\":\"\",\"serviceName\":\"settlement-service\",\"protocol\":\"HTTP\","
                + "\"direction\":\"SERVER\",\"httpMethod\":\"POST\",\"target\":\"/settlement\","
                + "\"statusCode\":200,\"eventTime\":\"2026-08-18T00:00:00Z\",\"durationMillis\":1,\"errorSummary\":\"\"}";
        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(APPLICATION_JSON).content("{\"events\":[" + event + "]}"))
                .andExpect(status().isAccepted());
    }
}
