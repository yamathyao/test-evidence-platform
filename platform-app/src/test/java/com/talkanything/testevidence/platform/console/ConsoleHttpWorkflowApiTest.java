package com.talkanything.testevidence.platform.console;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.talkanything.testevidence.platform.run.HttpTriggerClient;
import com.talkanything.testevidence.platform.run.HttpTriggerResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsoleHttpWorkflowApiTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private HttpTriggerClient triggerClient;

    @Test
    void createsAndRunsHttpCaseWithStatusAndJsonPathAssertions() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201,
                "{\"data\":{\"id\":\"case-1\"}}"));
        String profileId = createProfile();
        String testCaseId = createHttpCase(profileId);

        mockMvc.perform(post("/api/test-cases/{id}/runs", testCaseId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.assertionResults[0].type").value("HTTP_STATUS"))
                .andExpect(jsonPath("$.assertionResults[0].status").value("PASSED"))
                .andExpect(jsonPath("$.assertionResults[1].type").value("HTTP_JSON_PATH"))
                .andExpect(jsonPath("$.assertionResults[1].status").value("PASSED"));

        ArgumentCaptor<JsonNode> config = ArgumentCaptor.forClass(JsonNode.class);
        verify(triggerClient).execute(config.capture(), any());
        org.junit.jupiter.api.Assertions.assertEquals("POST", config.getValue().path("method").asText());
        org.junit.jupiter.api.Assertions.assertEquals("console", config.getValue().path("headers").path("X-Source").asText());
        org.junit.jupiter.api.Assertions.assertEquals("{\"id\":\"case-1\"}", config.getValue().path("body").asText());
    }

    @Test
    void createsHttpCaseWithMysqlScalarAssertion() throws Exception {
        String profileId = createProfile();
        String request = "{\"name\":\"console mysql\",\"profileId\":\"" + profileId + "\","
                + "\"triggerType\":\"HTTP\",\"triggerConfig\":{\"method\":\"POST\","
                + "\"url\":\"http://localhost/orders\",\"body\":\"{\\\"orderNo\\\":\\\"case-1\\\"}\"},"
                + "\"timeoutSeconds\":30,\"assertions\":[{\"sequenceNo\":1,\"type\":\"MYSQL_SCALAR\","
                + "\"definition\":{\"sql\":\"SELECT status FROM orders WHERE order_no = ?\","
                + "\"parameters\":[{\"source\":\"REQUEST_JSON_PATH\",\"jsonPath\":\"$.orderNo\"}],"
                + "\"expected\":\"PAID\",\"waitSeconds\":2}}]}";

        mockMvc.perform(post("/api/test-cases").contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assertions[0].type").value("MYSQL_SCALAR"))
                .andExpect(jsonPath("$.assertions[0].definition.sql")
                        .value("SELECT status FROM orders WHERE order_no = ?"));
    }

    private String createProfile() throws Exception {
        String response = mockMvc.perform(post("/api/capture-profiles").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"console http " + System.nanoTime() + "\",\"version\":1,\"definition\":{}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return response.replaceFirst("^\\{\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }

    private String createHttpCase(String profileId) throws Exception {
        String request = "{\"name\":\"console http\",\"profileId\":\"" + profileId + "\","
                + "\"triggerType\":\"HTTP\",\"triggerConfig\":{\"method\":\"POST\","
                + "\"url\":\"http://localhost/orders\",\"headers\":{\"X-Source\":\"console\","
                + "\"Content-Type\":\"application/json\"},\"body\":\"{\\\"id\\\":\\\"case-1\\\"}\"},"
                + "\"timeoutSeconds\":30,\"assertions\":[{\"sequenceNo\":1,\"type\":\"HTTP_STATUS\","
                + "\"definition\":{\"expected\":201}},{\"sequenceNo\":2,\"type\":\"HTTP_JSON_PATH\","
                + "\"definition\":{\"jsonPath\":\"$.data.id\",\"expected\":\"case-1\"}}]}";
        String response = mockMvc.perform(post("/api/test-cases").contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return response.replaceFirst("^\\{\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
    }
}
