package com.talkanything.testevidence.platform.evidence;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvidenceControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private CaptureProfileService profileService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService runService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EvidenceEventRepository evidenceRepository;
    @Autowired private AgentDeliveryDiagnosticRepository diagnosticRepository;
    @MockBean private HttpTriggerClient triggerClient;

    @Test
    void rollsBackEvidenceWhenDiagnosticProfileDoesNotMatchRun() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String diagnostic = diagnostic(run, UUID.randomUUID(), 1, "order-service", 1, 0);

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[" + event(run, "profile-mismatch", "") + "],\"diagnostics\":[" + diagnostic + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertTrue(evidenceRepository.findByTestRunIdOrderByEventTimeAscIdAsc(run.getId()).isEmpty());
        assertTrue(diagnosticRepository.findByTestRunIdOrderByServiceNameAsc(run.getId()).isEmpty());
    }

    @ParameterizedTest
    @MethodSource("invalidDiagnostics")
    void rejectsInvalidDiagnosticWithoutPersistingBatch(String serviceName, long droppedEvidenceCount,
                                                        long deliveryFailureCount) throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String diagnostic = diagnostic(run, run.getProfileId(), 1, serviceName, droppedEvidenceCount, deliveryFailureCount);

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[" + event(run, "invalid-diagnostic", "") + "],\"diagnostics\":[" + diagnostic + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertTrue(evidenceRepository.findByTestRunIdOrderByEventTimeAscIdAsc(run.getId()).isEmpty());
        assertTrue(diagnosticRepository.findByTestRunIdOrderByServiceNameAsc(run.getId()).isEmpty());
    }
    @Test
    void acceptsValidBatchAndReturnsTraceTree() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String event = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"trace-1\",\"spanId\":\"span-1\",\"parentSpanId\":\"\","
                + "\"serviceName\":\"order-service\",\"protocol\":\"HTTP\",\"direction\":\"SERVER\","
                + "\"httpMethod\":\"POST\",\"target\":\"/orders\",\"statusCode\":201,"
                + "\"eventTime\":\"2026-08-13T01:00:00Z\",\"durationMillis\":12,\"errorSummary\":\"\"}";

        String diagnostic = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"serviceName\":\"order-service\",\"droppedEvidenceCount\":2,"
                + "\"deliveryFailureCount\":1,\"lastFailure\":\"ConnectException\"}";

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[" + event + "],\"diagnostics\":[" + diagnostic + "]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(1));
        mockMvc.perform(get("/api/runs/{id}", run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceDeliveryDiagnostics.droppedEvidenceCount").value(2))
                .andExpect(jsonPath("$.evidenceDeliveryDiagnostics.deliveryFailureCount").value(1))
                .andExpect(jsonPath("$.evidenceDeliveryDiagnostics.agents[0].serviceName").value("order-service"))
                .andExpect(jsonPath("$.evidenceDeliveryDiagnostics.agents[0].lastFailure").value("ConnectException"));
        mockMvc.perform(get("/api/runs/{id}/trace", run.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roots[0].spanId").value("span-1"));
    }

    @Test
    void rejectsInvalidAgentToken() throws Exception {
        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void representsChildEventBelowMatchingParentSpan() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String parent = event(run, "parent", "");
        String child = event(run, "child", "parent");

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + parent + "," + child + "]}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/runs/{id}/trace", run.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roots[0].spanId").value("parent"))
                .andExpect(jsonPath("$.roots[0].children[0].spanId").value("child"));
    }

    @Test
    void doesNotConnectSameSpanIdAcrossDifferentTraces() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String query = event(run, "trace-query", "span-1", "");
        String update = event(run, "trace-update", "span-1", "");
        String fulfillment = event(run, "trace-update", "span-2", "span-1");

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + query + "," + update + "," + fulfillment + "]}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        var roots = objectMapper.readTree(mockMvc.perform(get("/api/runs/{id}/trace", run.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("roots");
        assertEquals(2, roots.size());
        Set<String> traceIds = new java.util.HashSet<String>();
        int updateChildren = 0;
        for (var root : roots) {
            traceIds.add(root.path("traceId").asText());
            if ("trace-update".equals(root.path("traceId").asText())) updateChildren = root.path("children").size();
        }
        assertEquals(Set.of("trace-query", "trace-update"), traceIds);
        assertEquals(1, updateChildren);
    }

    @Test
    void acceptsJdbcEvidenceAndReturnsItInTraceTree() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String event = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"trace-jdbc\",\"spanId\":\"span-jdbc\","
                + "\"parentSpanId\":\"\",\"serviceName\":\"order-service\",\"protocol\":\"JDBC\","
                + "\"direction\":\"CLIENT\",\"target\":\"mysql\",\"eventTime\":\"2026-08-14T01:00:00Z\","
                + "\"durationMillis\":6,\"errorSummary\":\"\",\"jdbcOperation\":\"UPDATE\","
                + "\"sqlTemplate\":\"UPDATE orders SET status = ? WHERE order_no = ?\",\"jdbcParameters\":[{"
                + "\"index\":1,\"setter\":\"setString\",\"valueLength\":4,\"sha256\":\"5b75daab06a2ef21\"}]}";

        String diagnostic = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"serviceName\":\"order-service\",\"droppedEvidenceCount\":2,"
                + "\"deliveryFailureCount\":1,\"lastFailure\":\"ConnectException\"}";

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[" + event + "],\"diagnostics\":[" + diagnostic + "]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(1));
        mockMvc.perform(get("/api/runs/{id}", run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceDeliveryDiagnostics.droppedEvidenceCount").value(2))
                .andExpect(jsonPath("$.evidenceDeliveryDiagnostics.deliveryFailureCount").value(1))
                .andExpect(jsonPath("$.evidenceDeliveryDiagnostics.agents[0].serviceName").value("order-service"))
                .andExpect(jsonPath("$.evidenceDeliveryDiagnostics.agents[0].lastFailure").value("ConnectException"));
        mockMvc.perform(get("/api/runs/{id}/trace", run.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roots[0].jdbcOperation").value("UPDATE"))
                .andExpect(jsonPath("$.roots[0].sqlTemplate").value("UPDATE orders SET status = ? WHERE order_no = ?"))
                .andExpect(jsonPath("$.roots[0].jdbcParameters[0].sha256").value("5b75daab06a2ef21"));
    }

    @Test
    void acceptsHttpClientAndStatementEvidenceInOneTrace() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String http = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"trace-client\",\"spanId\":\"span-client\",\"parentSpanId\":\"\","
                + "\"serviceName\":\"order-service\",\"protocol\":\"HTTP\",\"direction\":\"CLIENT\","
                + "\"httpMethod\":\"GET\",\"target\":\"http://inventory/items\",\"statusCode\":200,"
                + "\"eventTime\":\"2026-08-26T01:00:00Z\",\"durationMillis\":4,\"errorSummary\":\"\"}";
        String jdbc = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"trace-client\",\"spanId\":\"span-jdbc\",\"parentSpanId\":\"span-client\","
                + "\"serviceName\":\"order-service\",\"protocol\":\"JDBC\",\"direction\":\"CLIENT\",\"target\":\"mysql\","
                + "\"eventTime\":\"2026-08-26T01:00:00Z\",\"durationMillis\":2,\"errorSummary\":\"\","
                + "\"jdbcOperation\":\"SELECT\",\"sqlTemplate\":\"SELECT * FROM orders WHERE order_no = ?\",\"jdbcParameters\":[]}";

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + http + "," + jdbc + "]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(2));

        mockMvc.perform(get("/api/runs/{id}/trace", run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roots[0].protocol").value("HTTP"))
                .andExpect(jsonPath("$.roots[0].direction").value("CLIENT"))
                .andExpect(jsonPath("$.roots[0].target").value("http://inventory/items"))
                .andExpect(jsonPath("$.roots[0].children[0].protocol").value("JDBC"))
                .andExpect(jsonPath("$.roots[0].children[0].jdbcOperation").value("SELECT"))
                .andExpect(jsonPath("$.roots[0].children[0].jdbcParameters").isEmpty());
    }

    @Test
    void returnsChildServerPayloadWhenClientEvidenceHasNoLocalPayload() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String client = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"trace-client-payload\",\"spanId\":\"client\",\"parentSpanId\":\"\","
                + "\"serviceName\":\"order-service\",\"protocol\":\"HTTP\",\"direction\":\"CLIENT\","
                + "\"httpMethod\":\"POST\",\"target\":\"http://fulfillment/orders\",\"statusCode\":200,"
                + "\"eventTime\":\"2026-08-27T01:00:00Z\",\"durationMillis\":4,\"errorSummary\":\"\"}";
        String server = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"trace-client-payload\",\"spanId\":\"server\",\"parentSpanId\":\"client\","
                + "\"serviceName\":\"fulfillment-service\",\"protocol\":\"HTTP\",\"direction\":\"SERVER\","
                + "\"httpMethod\":\"POST\",\"target\":\"/orders\",\"statusCode\":200,"
                + "\"eventTime\":\"2026-08-27T01:00:01Z\",\"durationMillis\":3,\"errorSummary\":\"\","
                + "\"httpPayload\":{\"requestContentType\":\"application/json\",\"responseContentType\":\"application/json\","
                + "\"requestStatus\":\"CAPTURED\",\"responseStatus\":\"CAPTURED\",\"requestBody\":\"{\\\"orderNo\\\":\\\"ORD-1\\\"}\","
                + "\"responseBody\":\"{\\\"status\\\":\\\"FULFILLED\\\"}\",\"requestTruncated\":false,\"responseTruncated\":false}}";

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + client + "," + server + "]}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/runs/{id}/trace/http-payloads/client", run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceSpanId").value("server"))
                .andExpect(jsonPath("$.requestBody").value("{\"orderNo\":\"ORD-1\"}"))
                .andExpect(jsonPath("$.responseBody").value("{\"status\":\"FULFILLED\"}"));
    }

    @Test
    void persistsDubboClientAndServerAsParentChildTraceNodes() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String client = dubboEvent(run, "client", "", "CLIENT");
        String server = dubboEvent(run, "server", "client", "SERVER");

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + client + "," + server + "]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(2));

        mockMvc.perform(get("/api/runs/{id}/trace", run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roots[0].protocol").value("DUBBO"))
                .andExpect(jsonPath("$.roots[0].direction").value("CLIENT"))
                .andExpect(jsonPath("$.roots[0].jdbcOperation").doesNotExist())
                .andExpect(jsonPath("$.roots[0].children[0].direction").value("SERVER"))
                .andExpect(jsonPath("$.roots[0].children[0].jdbcParameters").doesNotExist());
    }

    @Test
    void keepsBlackboxRuleActiveAfterEvidenceDuringCaptureWindow() throws Exception {
        TestRun run = runService.startBlackbox(createBrowserCase(), Map.of("orderNo", "ORD-1", "tenantId", "tenant-a"), null);

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + event(run, "http", "") + "]}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/internal/v1/blackbox-correlation-rules?service=order-service")
                        .header("X-Test-Agent-Token", "test-agent-token"))
                .andExpect(status().isOk())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(result.getResponse().getContentAsString().isEmpty()));
    }

    private static Stream<Arguments> invalidDiagnostics() {
        return Stream.of(
                Arguments.of("", 0L, 0L),
                Arguments.of("order-service", -1L, 0L),
                Arguments.of("order-service", 0L, -1L));
    }

    private String diagnostic(TestRun run, UUID profileId, int profileVersion, String serviceName,
                              long droppedEvidenceCount, long deliveryFailureCount) {
        return "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + profileId
                + "\",\"profileVersion\":" + profileVersion + ",\"serviceName\":\"" + serviceName
                + "\",\"droppedEvidenceCount\":" + droppedEvidenceCount + ",\"deliveryFailureCount\":"
                + deliveryFailureCount + ",\"lastFailure\":\"timeout\"}";
    }
    private TestCase createCase() throws Exception {
        CaptureProfile profile = profileService.create("ingest-profile-" + System.nanoTime(), 1, "{}");
        return testCaseService.create(new TestCaseRequest("ingest case", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/evidence\"}"), 30, List.of()));
    }

    private TestCase createBrowserCase() throws Exception {
        String definition = "{\"blackboxCorrelation\":{\"defaultTtlSeconds\":600,\"retentionSeconds\":3600,\"singleUse\":true,"
                + "\"targetServices\":[\"order-service\"],\"matchGroups\":[{\"name\":\"order\",\"matchers\":["
                + "{\"field\":\"orderNo\",\"locations\":[\"QUERY\"],\"match\":\"EXACT\"},"
                + "{\"field\":\"tenantId\",\"locations\":[\"HEADER\"],\"match\":\"EXACT\"}]}]}}";
        CaptureProfile profile = profileService.create("blackbox-ingest-profile-" + System.nanoTime(), 1, definition);
        return testCaseService.create(new TestCaseRequest("blackbox ingest", profile.getId(), TriggerType.BROWSER,
                objectMapper.readTree("{}"), 60, List.of()));
    }

    private String event(TestRun run, String spanId, String parentSpanId) {
        return event(run, "trace-2", spanId, parentSpanId);
    }

    private String event(TestRun run, String traceId, String spanId, String parentSpanId) {
        return "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"" + traceId + "\",\"spanId\":\"" + spanId
                + "\",\"parentSpanId\":\"" + parentSpanId + "\",\"serviceName\":\"order-service\","
                + "\"protocol\":\"HTTP\",\"direction\":\"SERVER\",\"httpMethod\":\"GET\","
                + "\"target\":\"/orders\",\"statusCode\":200,\"eventTime\":\"2026-08-13T01:00:00Z\","
                + "\"durationMillis\":1,\"errorSummary\":\"\"}";
    }

    private String dubboEvent(TestRun run, String spanId, String parentSpanId, String direction) {
        return "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"trace-dubbo\",\"spanId\":\"" + spanId
                + "\",\"parentSpanId\":\"" + parentSpanId + "\",\"serviceName\":\"order-service\","
                + "\"protocol\":\"DUBBO\",\"direction\":\"" + direction + "\",\"httpMethod\":null,"
                + "\"target\":\"com.example.EchoService#echo\",\"statusCode\":null,"
                + "\"eventTime\":\"2026-08-17T01:00:00Z\",\"durationMillis\":1,\"errorSummary\":\"\","
                + "\"jdbcOperation\":null,\"sqlTemplate\":null,\"jdbcParameters\":null}";
    }
}
