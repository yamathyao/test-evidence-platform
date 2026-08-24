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
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @MockBean private HttpTriggerClient triggerClient;

    @Test
    void acceptsValidBatchAndReturnsTraceTree() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(createCase());
        String event = "{\"testRunId\":\"" + run.getId() + "\",\"profileId\":\"" + run.getProfileId()
                + "\",\"profileVersion\":1,\"traceId\":\"trace-1\",\"spanId\":\"span-1\",\"parentSpanId\":\"\","
                + "\"serviceName\":\"order-service\",\"protocol\":\"HTTP\",\"direction\":\"SERVER\","
                + "\"httpMethod\":\"POST\",\"target\":\"/orders\",\"statusCode\":201,"
                + "\"eventTime\":\"2026-08-13T01:00:00Z\",\"durationMillis\":12,\"errorSummary\":\"\"}";

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + event + "]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(1));
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

        mockMvc.perform(post("/internal/v1/evidence/batches").header("X-Test-Agent-Token", "test-agent-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"events\":[" + event + "]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.accepted").value(1));
        mockMvc.perform(get("/api/runs/{id}/trace", run.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roots[0].jdbcOperation").value("UPDATE"))
                .andExpect(jsonPath("$.roots[0].sqlTemplate").value("UPDATE orders SET status = ? WHERE order_no = ?"))
                .andExpect(jsonPath("$.roots[0].jdbcParameters[0].sha256").value("5b75daab06a2ef21"));
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
