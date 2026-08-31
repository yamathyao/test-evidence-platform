package com.talkanything.testevidence.platform.run;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.evidence.AgentDeliveryDiagnosticRepository;
import com.talkanything.testevidence.platform.evidence.EvidenceEvent;
import com.talkanything.testevidence.platform.evidence.EvidenceEventRepository;
import com.talkanything.testevidence.platform.evidence.HttpPayloadEvidence;
import com.talkanything.testevidence.platform.evidence.HttpPayloadEvidenceRepository;
import com.talkanything.testevidence.platform.evidence.ProtocolPayloadEvidence;
import com.talkanything.testevidence.platform.evidence.ProtocolPayloadEvidenceRepository;
import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RunRetentionServiceTest {
    private static final Instant CLEANUP_TIME = Instant.parse("2026-08-25T00:00:00Z");

    @Autowired private RunRetentionService retentionService;
    @Autowired private TestRunRepository runRepository;
    @Autowired private AssertionResultRepository assertionRepository;
    @Autowired private EvidenceEventRepository evidenceRepository;
    @Autowired private HttpPayloadEvidenceRepository payloadRepository;
    @Autowired private ProtocolPayloadEvidenceRepository protocolPayloadRepository;
    @Autowired private AgentDeliveryDiagnosticRepository diagnosticRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CaptureProfileService profileService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService runService;
    @MockBean private HttpTriggerClient triggerClient;

    @BeforeEach
    void setUp() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
    }

    @Test
    void deletesOnlyExpiredPayloads() throws Exception {
        TestRun expiredRun = startRunWithEvidenceAndDiagnostic("expired-payload");
        TestRun retainedRun = startRunWithEvidenceAndDiagnostic("retained-payload");
        HttpPayloadEvidence expired = payload(expiredRun);
        HttpPayloadEvidence retained = payload(retainedRun);
        expired.shortenExpiry(CLEANUP_TIME.minusSeconds(1));
        retained.shortenExpiry(CLEANUP_TIME.plusSeconds(1));
        payloadRepository.saveAll(List.of(expired, retained));

        retentionService.cleanExpired(CLEANUP_TIME);

        assertTrue(payloadRepository.findByEvidenceEvent_Id(evidenceEvent(expiredRun).getId()).isEmpty());
        assertTrue(payloadRepository.findByEvidenceEvent_Id(evidenceEvent(retainedRun).getId()).isPresent());
    }

    @Test
    void deletesOnlyExpiredProtocolPayloads() throws Exception {
        TestRun expiredRun = startRunWithProtocolPayloadAndDiagnostic("expired-protocol-payload");
        TestRun retainedRun = startRunWithProtocolPayloadAndDiagnostic("retained-protocol-payload");
        ProtocolPayloadEvidence expired = protocolPayload(expiredRun);
        ProtocolPayloadEvidence retained = protocolPayload(retainedRun);
        expired.shortenExpiry(CLEANUP_TIME.minusSeconds(1));
        retained.shortenExpiry(CLEANUP_TIME.plusSeconds(1));
        protocolPayloadRepository.saveAll(List.of(expired, retained));

        retentionService.cleanExpired(CLEANUP_TIME);

        assertTrue(protocolPayloadRepository.findByEvidenceEvent_Id(evidenceEvent(expiredRun).getId()).isEmpty());
        assertTrue(protocolPayloadRepository.findByEvidenceEvent_Id(evidenceEvent(retainedRun).getId()).isPresent());
    }

    @Test
    void deletesExpiredFinishedRunAndAllDependentEvidence() throws Exception {
        TestRun expired = startRunWithEvidenceAndDiagnostic("expired-run");
        EvidenceEvent evidence = evidenceEvent(expired);
        jdbcTemplate.update("update test_run set finished_at = ? where id = ?",
                Timestamp.from(CLEANUP_TIME.minusSeconds(31L * 86400L)), expired.getId());

        retentionService.cleanExpired(CLEANUP_TIME);

        assertTrue(runRepository.findById(expired.getId()).isEmpty());
        assertTrue(assertionRepository.findByTestRunIdOrderBySequenceNoAsc(expired.getId()).isEmpty());
        assertTrue(evidenceRepository.findByTestRunIdOrderByEventTimeAscIdAsc(expired.getId()).isEmpty());
        assertTrue(payloadRepository.findByEvidenceEvent_Id(evidence.getId()).isEmpty());
        assertTrue(diagnosticRepository.findByTestRunIdOrderByServiceNameAsc(expired.getId()).isEmpty());
    }

    @Test
    void deletesProtocolPayloadWithExpiredFinishedRun() throws Exception {
        TestRun expired = startRunWithProtocolPayloadAndDiagnostic("expired-protocol-run");
        EvidenceEvent evidence = evidenceEvent(expired);
        jdbcTemplate.update("update test_run set finished_at = ? where id = ?",
                Timestamp.from(CLEANUP_TIME.minusSeconds(31L * 86400L)), expired.getId());

        retentionService.cleanExpired(CLEANUP_TIME);

        assertTrue(runRepository.findById(expired.getId()).isEmpty());
        assertTrue(protocolPayloadRepository.findByEvidenceEvent_Id(evidence.getId()).isEmpty());
    }

    @Test
    void keepsUnfinishedAndRecentlyFinishedRuns() throws Exception {
        TestRun unfinished = runRepository.save(new TestRun(createCase("unfinished-run"), "{}"));
        TestRun recent = startRunWithEvidenceAndDiagnostic("recent-run");
        jdbcTemplate.update("update test_run set finished_at = ? where id = ?",
                Timestamp.from(CLEANUP_TIME.minusSeconds(86400L)), recent.getId());

        retentionService.cleanExpired(CLEANUP_TIME);

        assertTrue(runRepository.existsById(unfinished.getId()));
        assertTrue(runRepository.existsById(recent.getId()));
    }

    private TestRun startRunWithEvidenceAndDiagnostic(String name) throws Exception {
        return startRunWithPayloadAndDiagnostic(name, "httpPayload");
    }

    private TestRun startRunWithProtocolPayloadAndDiagnostic(String name) throws Exception {
        return startRunWithPayloadAndDiagnostic(name, "protocolPayload");
    }

    private TestRun startRunWithPayloadAndDiagnostic(String name, String payloadField) throws Exception {
        TestRun run = runService.start(createCase(name));
        String event = event(run, name).replace("\"httpPayload\":", "\"" + payloadField + "\":");
        String diagnostic = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"serviceName\":\"retention-service\",\"droppedEvidenceCount\":1,"
                + "\"deliveryFailureCount\":1,\"lastFailure\":\"timeout\"}";
        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(APPLICATION_JSON).content("{\"events\":[" + event + "],\"diagnostics\":[" + diagnostic + "]}"))
                .andExpect(status().isAccepted());
        return run;
    }

    private TestCase createCase(String name) throws Exception {
        CaptureProfile profile = profileService.create("retention-profile-" + name + "-" + System.nanoTime(), 1, "{}");
        return testCaseService.create(new TestCaseRequest(name, profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/retention\"}"), 30, true,
                List.of(new TestCaseRequest.AssertionRequest(1, "HTTP_STATUS", objectMapper.readTree("{\"expected\":200}")))));
    }

    private String event(TestRun run, String spanId) {
        return "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"retention-trace-" + spanId + "\",\"spanId\":\"" + spanId
                + "\",\"parentSpanId\":\"\",\"serviceName\":\"retention-service\",\"protocol\":\"HTTP\","
                + "\"direction\":\"SERVER\",\"httpMethod\":\"GET\",\"target\":\"/retention\",\"statusCode\":200,"
                + "\"eventTime\":\"2026-08-25T00:00:00Z\",\"durationMillis\":1,\"errorSummary\":\"\",\"httpPayload\":{"
                + "\"requestContentType\":\"application/json\",\"responseContentType\":\"application/json\","
                + "\"requestStatus\":\"CAPTURED\",\"responseStatus\":\"CAPTURED\",\"requestBody\":\"{}\","
                + "\"responseBody\":\"{}\",\"requestTruncated\":false,\"responseTruncated\":false}}";
    }

    private HttpPayloadEvidence payload(TestRun run) {
        return payloadRepository.findByEvidenceEvent_Id(evidenceEvent(run).getId()).orElseThrow();
    }

    private ProtocolPayloadEvidence protocolPayload(TestRun run) {
        return protocolPayloadRepository.findByEvidenceEvent_Id(evidenceEvent(run).getId()).orElseThrow();
    }

    private EvidenceEvent evidenceEvent(TestRun run) {
        return evidenceRepository.findByTestRunIdOrderByEventTimeAscIdAsc(run.getId()).get(0);
    }
}
