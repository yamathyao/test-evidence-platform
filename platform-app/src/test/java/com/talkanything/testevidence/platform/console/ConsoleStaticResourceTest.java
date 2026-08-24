package com.talkanything.testevidence.platform.console;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
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
class ConsoleStaticResourceTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void servesLocalConsoleWithoutExternalResources() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("console.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("console.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("trace-topology.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("triggerType")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http-method")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http-url")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http-headers")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http-body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http-status-enabled")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http-json-path-enabled")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mysql-scalar-enabled")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mysql-scalar-sql")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mysql-scalar-parameters")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mysql-scalar-expected")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mysql-scalar-wait-seconds")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("http://"))));
        mockMvc.perform(get("/console.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HTTP_JSON_PATH")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MYSQL_SCALAR")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mysqlParameterRow")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/runs")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("const formElement = event.currentTarget")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("formElement.reset()")));
        mockMvc.perform(get("/trace-topology.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("buildSpanGraph")));
        mockMvc.perform(get("/console.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grid-template-columns:minmax(0,1fr) auto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".mysql-parameter-row")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("overflow-wrap:anywhere")));
    }
}
