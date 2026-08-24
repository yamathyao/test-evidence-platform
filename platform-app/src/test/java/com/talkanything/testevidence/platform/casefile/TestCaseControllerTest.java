package com.talkanything.testevidence.platform.casefile;

import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestCaseControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private CaptureProfileService profileService;
    private CaptureProfile profile;

    @BeforeEach
    void createProfile() {
        profile = profileService.create("case-profile-" + System.nanoTime(), 1, "{}");
    }

    @Test
    void createsHttpTestCaseWithAssertions() throws Exception {
        String request = "{\"name\":\"create order\",\"profileId\":\"" + profile.getId()
                + "\",\"triggerType\":\"HTTP\",\"triggerConfig\":{\"method\":\"POST\",\"url\":\"http://localhost/orders\"},"
                + "\"timeoutSeconds\":30,\"httpPayloadCaptureEnabled\":true,\"assertions\":[{\"sequenceNo\":1,\"type\":\"HTTP_STATUS\",\"definition\":{\"expected\":200}}]}";

        mockMvc.perform(post("/api/test-cases").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("create order"))
                .andExpect(jsonPath("$.httpPayloadCaptureEnabled").value(true))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.assertions[0].sequenceNo").value(1));
    }

    @Test
    void completesAndReopensTestCase() throws Exception {
        String request = "{\"name\":\"lifecycle\",\"profileId\":\"" + profile.getId()
                + "\",\"triggerType\":\"HTTP\",\"triggerConfig\":{},\"timeoutSeconds\":30,\"assertions\":[]}";
        String id = mockMvc.perform(post("/api/test-cases").contentType(MediaType.APPLICATION_JSON).content(request))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");

        mockMvc.perform(post("/api/test-cases/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        mockMvc.perform(post("/api/test-cases/{id}/reopen", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void replacesCaseAssertions() throws Exception {
        String create = "{\"name\":\"old\",\"profileId\":\"" + profile.getId() + "\",\"triggerType\":\"HTTP\",\"triggerConfig\":{\"url\":\"http://localhost/a\"},\"timeoutSeconds\":30,\"assertions\":[]}";
        String id = mockMvc.perform(post("/api/test-cases").contentType(MediaType.APPLICATION_JSON).content(create))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\\\"id\\\":\\\"([^\\\"]+).*", "$1");
        String update = "{\"name\":\"new\",\"profileId\":\"" + profile.getId() + "\",\"triggerType\":\"HTTP\",\"triggerConfig\":{\"url\":\"http://localhost/b\"},\"timeoutSeconds\":20,\"assertions\":[{\"sequenceNo\":1,\"type\":\"HTTP_STATUS\",\"definition\":{}}]}";

        mockMvc.perform(put("/api/test-cases/{id}", id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"))
                .andExpect(jsonPath("$.timeoutSeconds").value(20))
                .andExpect(jsonPath("$.assertions.length()").value(1));
    }

    @Test
    void acceptsMysqlScalarForHttpAndRejectsItForBrowserOrInvalidSql() throws Exception {
        String mysqlAssertion = "{\"sequenceNo\":1,\"type\":\"MYSQL_SCALAR\",\"definition\":{\"sql\":\"SELECT status FROM orders WHERE order_no = ?\",\"parameters\":[{\"source\":\"REQUEST_JSON_PATH\",\"jsonPath\":\"$.orderNo\"}],\"expected\":\"PAID\"}}";
        String http = "{\"name\":\"mysql-http\",\"profileId\":\"" + profile.getId()
                + "\",\"triggerType\":\"HTTP\",\"triggerConfig\":{\"method\":\"POST\",\"url\":\"http://localhost/orders\",\"body\":\"{\\\"orderNo\\\":\\\"order-1\\\"}\"},\"timeoutSeconds\":30,\"assertions\":[" + mysqlAssertion + "]}";
        String browser = http.replace("\"triggerType\":\"HTTP\"", "\"triggerType\":\"BROWSER\"");
        String invalidSql = http.replace("SELECT status FROM orders WHERE order_no = ?", "UPDATE orders SET status = ?");

        mockMvc.perform(post("/api/test-cases").contentType(MediaType.APPLICATION_JSON).content(http))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assertions[0].type").value("MYSQL_SCALAR"));
        mockMvc.perform(post("/api/test-cases").contentType(MediaType.APPLICATION_JSON).content(browser))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/test-cases").contentType(MediaType.APPLICATION_JSON).content(invalidSql))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsLongBrowserCaptureWindow() throws Exception {
        String request = "{\"name\":\"browser window\",\"profileId\":\"" + profile.getId()
                + "\",\"triggerType\":\"BROWSER\",\"triggerConfig\":{},\"timeoutSeconds\":1800,\"assertions\":[]}";

        mockMvc.perform(post("/api/test-cases").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeoutSeconds").value(1800));
    }
}
