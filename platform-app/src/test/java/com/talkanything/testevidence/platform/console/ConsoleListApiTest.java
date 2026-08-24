package com.talkanything.testevidence.platform.console;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import com.talkanything.testevidence.platform.run.HttpTriggerClient;
import com.talkanything.testevidence.platform.run.HttpTriggerResponse;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsoleListApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private CaptureProfileService profileService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private HttpTriggerClient triggerClient;

    @Test
    void listsNewestCaptureProfilesForConsole() throws Exception {
        profileService.create("console-profile", 1, "{}");

        mockMvc.perform(get("/api/capture-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("console-profile"))
                .andExpect(jsonPath("$[0].definition").doesNotExist());
    }

    @Test
    void pagesCaptureProfilesByNameAndVersion() throws Exception {
        profileService.create("payment capture", 1, "{}");
        profileService.create("payment capture", 2, "{}");
        profileService.create("order capture", 2, "{}");

        mockMvc.perform(get("/api/capture-profiles/page")
                        .param("query", "payment")
                        .param("version", "2")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("payment capture"))
                .andExpect(jsonPath("$.content[0].version").value(2))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void listsCaptureProfileOptionsByName() throws Exception {
        profileService.create("payment option capture", 1, "{}");
        profileService.create("order option capture", 1, "{}");

        mockMvc.perform(get("/api/capture-profiles/options").param("query", "payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("payment option capture"))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].definition").doesNotExist());
    }

    @Test
    void pagesActiveTestCasesByProfileAndTriggerType() throws Exception {
        var profile = profileService.create("case page profile", 1, "{}");
        TestCase browserCase = testCaseService.create(new TestCaseRequest("browser page checkout", profile.getId(),
                TriggerType.BROWSER, objectMapper.readTree("{}"), 60, List.of()));
        TestCase completedCase = testCaseService.create(new TestCaseRequest("browser page completed", profile.getId(),
                TriggerType.BROWSER, objectMapper.readTree("{}"), 60, List.of()));
        testCaseService.complete(completedCase.getId());
        testCaseService.create(new TestCaseRequest("http page checkout", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/orders\"}"), 30, List.of()));

        mockMvc.perform(get("/api/test-cases/page")
                        .param("query", "browser page")
                        .param("triggerType", "BROWSER")
                        .param("completed", "false")
                        .param("profileId", profile.getId().toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value(browserCase.getName()))
                .andExpect(jsonPath("$.content[0].triggerType").value("BROWSER"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listsOnlyActiveTestCaseOptions() throws Exception {
        var profile = profileService.create("case option profile", 1, "{}");
        testCaseService.create(new TestCaseRequest("active option case", profile.getId(), TriggerType.BROWSER,
                objectMapper.readTree("{}"), 60, List.of()));
        TestCase completed = testCaseService.create(new TestCaseRequest("completed option case", profile.getId(),
                TriggerType.BROWSER, objectMapper.readTree("{}"), 60, List.of()));
        testCaseService.complete(completed.getId());

        mockMvc.perform(get("/api/test-cases/options").param("query", "option case"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("active option case"))
                .andExpect(jsonPath("$[0].triggerType").value("BROWSER"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void listsTestCasesAndRunsForConsole() throws Exception {
        TestCase testCase = httpCase();
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201, ""));
        mockMvc.perform(post("/api/test-cases/{id}/runs", testCase.getId()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/test-cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("console-case"))
                .andExpect(jsonPath("$[0].triggerType").value("HTTP"));
        mockMvc.perform(get("/api/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].testCaseName").value("console-case"))
                .andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
    }

    @Test
    void pagesRunsByCaseNameStatusAndTriggerType() throws Exception {
        TestCase testCase = httpCase("run page checkout");
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201, ""));
        mockMvc.perform(post("/api/test-cases/{id}/runs", testCase.getId())).andExpect(status().isCreated());

        mockMvc.perform(get("/api/runs/page")
                        .param("query", "run page")
                        .param("status", "SUCCEEDED")
                        .param("triggerType", "HTTP")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].testCaseName").value("run page checkout"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private TestCase httpCase() throws Exception {
        return httpCase("console-case");
    }

    private TestCase httpCase(String name) throws Exception {
        var profile = profileService.create(name + " profile", 1, "{}");
        return testCaseService.create(new TestCaseRequest(name, profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/orders\"}"), 30, List.of()));
    }
}
