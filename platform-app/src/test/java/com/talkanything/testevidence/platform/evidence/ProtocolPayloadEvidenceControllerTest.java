package com.talkanything.testevidence.platform.evidence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import com.talkanything.testevidence.platform.run.HttpTriggerClient;
import com.talkanything.testevidence.platform.run.HttpTriggerResponse;
import com.talkanything.testevidence.platform.run.TestRun;
import com.talkanything.testevidence.platform.run.TestRunService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtocolPayloadEvidenceControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CaptureProfileService profileService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService runService;
    @Autowired private EvidenceEventRepository evidenceRepository;
    @MockBean private HttpTriggerClient triggerClient;

    @Test
    void returnsDubboArgumentsAndResultFromUnifiedPayloadEndpoint() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());

        ingest(event(run, false));

        mockMvc.perform(get("/api/runs/{id}/trace/payloads/dubbo-client", run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocol").value("DUBBO"))
                .andExpect(jsonPath("$.sourceSpanId").value("dubbo-client"))
                .andExpect(jsonPath("$.requestStatus").value("CAPTURED"))
                .andExpect(jsonPath("$.requestBody").value("[\"ORD-1\"]"))
                .andExpect(jsonPath("$.responseStatus").value("CAPTURED"))
                .andExpect(jsonPath("$.responseBody").value("{\"fulfilled\":true}"));
    }

    @Test
    void rejectsEvidenceThatMixesLegacyAndUnifiedPayloads() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());

        mockMvc.perform(post("/internal/v1/evidence/batches")
                        .header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("events", List.of(event(run, true))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertTrue(evidenceRepository.findByTestRunIdOrderByEventTimeAscIdAsc(run.getId()).isEmpty());
    }

    @Test
    void rejectsProtocolPayloadWithBodyOver64KiB() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        Map<String, Object> event = event(run, false);
        Map<String, Object> payload = new LinkedHashMap<>(payload());
        payload.put("requestBody", "x".repeat(65_537));
        event.put("protocolPayload", payload);

        mockMvc.perform(post("/internal/v1/evidence/batches")
                        .header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("events", List.of(event)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertTrue(evidenceRepository.findByTestRunIdOrderByEventTimeAscIdAsc(run.getId()).isEmpty());
    }

    private void ingest(Map<String, Object> event) throws Exception {
        mockMvc.perform(post("/internal/v1/evidence/batches")
                        .header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("events", List.of(event)))))
                .andExpect(status().isAccepted());
    }

    private Map<String, Object> event(TestRun run, boolean includeLegacyPayload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("testRunId", run.getId());
        event.put("profileId", run.getProfileId());
        event.put("profileVersion", 1);
        event.put("traceId", "dubbo-trace");
        event.put("spanId", "dubbo-client");
        event.put("parentSpanId", "");
        event.put("serviceName", "order-service");
        event.put("protocol", "DUBBO");
        event.put("direction", "CLIENT");
        event.put("target", "com.example.FulfillmentProbeService#probe");
        event.put("eventTime", "2026-08-28T01:00:00Z");
        event.put("durationMillis", 4);
        event.put("errorSummary", "");
        event.put("protocolPayload", payload());
        if (includeLegacyPayload) event.put("httpPayload", payload());
        return event;
    }

    private Map<String, Object> payload() {
        return Map.of("requestContentType", "application/json", "responseContentType", "application/json",
                "requestStatus", "CAPTURED", "responseStatus", "CAPTURED", "requestBody", "[\"ORD-1\"]",
                "responseBody", "{\"fulfilled\":true}", "requestTruncated", false, "responseTruncated", false);
    }

    private TestCase createCase() throws Exception {
        CaptureProfile profile = profileService.create("protocol-payload-profile-" + System.nanoTime(), 1, "{}");
        return testCaseService.create(new TestCaseRequest("protocol payload", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/protocol\"}"), 30, true, List.of()));
    }
}
