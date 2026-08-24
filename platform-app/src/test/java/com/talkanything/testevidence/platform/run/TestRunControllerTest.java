package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestRunControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private CaptureProfileService profileService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private HttpTriggerClient triggerClient;

    @Test
    void createsAndFindsSuccessfulRun() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201, ""));
        TestCase testCase = httpCase();

        String body = mockMvc.perform(post("/api/test-cases/{id}/runs", testCase.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn().getResponse().getContentAsString();
        String runId = responseId(body);

        mockMvc.perform(get("/api/runs/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.snapshotAvailable").value(true))
                .andExpect(jsonPath("$.caseSnapshot.name").value("run api"))
                .andExpect(jsonPath("$.caseSnapshot.trigger.method").value("GET"));
    }

    @Test
    void keepsAnImmutableSanitizedCaseSnapshot() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        CaptureProfile profile = profileService.create("snapshot-profile-" + System.nanoTime(), 3, "{}");
        TestCase testCase = testCaseService.create(new TestCaseRequest("snapshot before", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"POST\",\"url\":\"http://localhost/orders\",\"body\":\"secret-body\",\"headers\":{\"Authorization\":\"secret-header\"}}"),
                30, List.of(new TestCaseRequest.AssertionRequest(1, "HTTP_STATUS", objectMapper.readTree("{\"expected\":200}")))));

        String runId = mockMvc.perform(post("/api/test-cases/{id}/runs", testCase.getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .transform(this::responseId);
        testCaseService.update(testCase.getId(), new TestCaseRequest("snapshot after", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"DELETE\",\"url\":\"http://localhost/changed\"}"), 20, List.of()));

        mockMvc.perform(get("/api/runs/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseSnapshot.name").value("snapshot before"))
                .andExpect(jsonPath("$.caseSnapshot.trigger.method").value("POST"))
                .andExpect(jsonPath("$.caseSnapshot.trigger.url").value("http://localhost/orders"))
                .andExpect(jsonPath("$.caseSnapshot.trigger.headerNames[0]").value("Authorization"))
                .andExpect(jsonPath("$.caseSnapshot.assertions[0].type").value("HTTP_STATUS"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(
                        result.getResponse().getContentAsString().contains("secret-body")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(
                        result.getResponse().getContentAsString().contains("secret-header")));
    }

    @Test
    void returnsAssertionResultDetailsForFailedRun() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201, ""));
        CaptureProfile profile = profileService.create("assertion-api-profile-" + System.nanoTime(), 1, "{}");
        TestCase testCase = testCaseService.create(new TestCaseRequest("assertion api", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/orders\"}"), 30,
                List.of(new TestCaseRequest.AssertionRequest(1, "HTTP_STATUS", objectMapper.readTree("{\"expected\":200}")))));

        mockMvc.perform(post("/api/test-cases/{id}/runs", testCase.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.assertionResults[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.assertionResults[0].status").value("FAILED"))
                .andExpect(jsonPath("$.assertionResults[0].expected.expected").value(200))
                .andExpect(jsonPath("$.assertionResults[0].actual.actual").value(201));
    }

    @Test
    void returnsJsonPathAssertionResultDetails() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201,
                "{\"data\":{\"orderNo\":\"ORD-001\"}}"));
        CaptureProfile profile = profileService.create("json-path-api-profile-" + System.nanoTime(), 1, "{}");
        TestCase testCase = testCaseService.create(new TestCaseRequest("json path api", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/orders\"}"), 30,
                List.of(new TestCaseRequest.AssertionRequest(1, "HTTP_JSON_PATH",
                        objectMapper.readTree("{\"jsonPath\":\"$.data.orderNo\",\"expected\":\"ORD-001\"}")))));

        mockMvc.perform(post("/api/test-cases/{id}/runs", testCase.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.assertionResults[0].status").value("PASSED"))
                .andExpect(jsonPath("$.assertionResults[0].actual.jsonPath").value("$.data.orderNo"))
                .andExpect(jsonPath("$.assertionResults[0].actual.actual").value("ORD-001"));
    }

    @Test
    void returnsNotFoundForUnknownTestCase() throws Exception {
        mockMvc.perform(get("/api/test-cases/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void startsBrowserBlackboxRunWithCorrelationData() throws Exception {
        TestCase testCase = browserCase();

        mockMvc.perform(post("/api/test-cases/{id}/blackbox-runs", testCase.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"correlationData\":{\"orderNo\":\"ORD-1\",\"tenantId\":\"tenant-a\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.rootTraceCount").value(0));
    }

    @Test
    void completesBrowserRunIntoDrainingWithRunMetadata() throws Exception {
        TestCase testCase = browserCase();
        String body = mockMvc.perform(post("/api/test-cases/{id}/blackbox-runs", testCase.getId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"correlationData\":{\"orderNo\":\"ORD-1\",\"tenantId\":\"tenant-a\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/runs/{id}/complete", responseId(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAINING"))
                .andExpect(jsonPath("$.drainingAt").isNotEmpty())
                .andExpect(jsonPath("$.finishReason").value("MANUAL"))
                .andExpect(jsonPath("$.rootTraceCount").value(0));
    }

    @Test
    void rejectsCompletingNonBrowserRun() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201, ""));
        TestCase testCase = httpCase();
        String body = mockMvc.perform(post("/api/test-cases/{id}/runs", testCase.getId()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String runId = responseId(body);

        mockMvc.perform(post("/api/runs/{id}/complete", runId))
                .andExpect(status().isConflict());
    }

    @Test
    void protectsBlackboxRuleDistributionAndDoesNotExposeFieldValues() throws Exception {
        TestCase testCase = browserCase();
        mockMvc.perform(post("/api/test-cases/{id}/blackbox-runs", testCase.getId())
                .contentType(APPLICATION_JSON)
                .content("{\"correlationData\":{\"orderNo\":\"ORD-1\",\"tenantId\":\"tenant-a\"}}"));

        mockMvc.perform(get("/internal/v1/blackbox-correlation-rules?service=order-service"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/blackbox-correlation-rules?service=order-service")
                        .header("X-Test-Agent-Token", "test-agent-token"))
                .andExpect(status().isOk())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(
                        result.getResponse().getContentAsString().contains("ORD-1")));
    }

    private TestCase httpCase() throws Exception {
        CaptureProfile profile = profileService.create("run-api-profile-" + System.nanoTime(), 1, "{}");
        return testCaseService.create(new TestCaseRequest("run api", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/orders\"}"), 30, List.of()));
    }

    private TestCase browserCase() throws Exception {
        String definition = "{\"blackboxCorrelation\":{\"defaultTtlSeconds\":600,\"retentionSeconds\":3600,"
                + "\"singleUse\":true,\"targetServices\":[\"order-service\"],\"matchGroups\":[{\"name\":\"order\","
                + "\"matchers\":[{\"field\":\"orderNo\",\"locations\":[\"QUERY\"],\"match\":\"EXACT\"},"
                + "{\"field\":\"tenantId\",\"locations\":[\"HEADER\"],\"match\":\"EXACT\"}]}]}}";
        CaptureProfile profile = profileService.create("browser-api-profile-" + System.nanoTime(), 1, definition);
        return testCaseService.create(new TestCaseRequest("browser api", profile.getId(), TriggerType.BROWSER,
                objectMapper.readTree("{}"), 60, List.of()));
    }

    private String responseId(String response) {
        return response.replaceFirst("^\\{\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }
}
