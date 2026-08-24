package com.talkanything.testevidence.platform.console;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsoleWorkflowApiTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void createsBrowserRunWithMultipleCorrelationFieldsWithoutEchoingValues() throws Exception {
        String definition = "{\"blackboxCorrelation\":{\"defaultTtlSeconds\":60,\"retentionSeconds\":300,"
                + "\"singleUse\":true,\"targetServices\":[\"console-service\"],\"matchGroups\":[{\"name\":\"browser\","
                + "\"matchers\":[{\"field\":\"orderNo\",\"locations\":[\"JSON_BODY\"],\"match\":\"EXACT\"},"
                + "{\"field\":\"tenantId\",\"locations\":[\"HEADER\"],\"match\":\"EXACT\"}]}]}}";
        String profileResponse = mockMvc.perform(post("/api/capture-profiles")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"console workflow\",\"version\":1,\"definition\":" + definition + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String profileId = profileResponse.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");

        String caseResponse = mockMvc.perform(post("/api/test-cases")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"console browser\",\"profileId\":\"" + profileId
                                + "\",\"triggerType\":\"BROWSER\",\"triggerConfig\":{},\"timeoutSeconds\":60,\"assertions\":[]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String testCaseId = caseResponse.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");

        String runResponse = mockMvc.perform(post("/api/test-cases/{id}/blackbox-runs", testCaseId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"correlationData\":{\"orderNo\":\"console-order\",\"tenantId\":\"console-tenant\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(runResponse.contains("console-order"));
        assertFalse(runResponse.contains("console-tenant"));
    }
}
