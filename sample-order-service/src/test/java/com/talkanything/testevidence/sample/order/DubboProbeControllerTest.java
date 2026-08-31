package com.talkanything.testevidence.sample.order;

import com.talkanything.testevidence.sample.dubbo.FulfillmentProbeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DubboProbeController.class)
class DubboProbeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DubboFulfillmentProbeClient client;

    @Test
    void invokesTheDubboProbeAndReturnsItsEvidenceLabel() throws Exception {
        when(client.echo("dubbo-order-1"))
                .thenReturn(new FulfillmentProbeResponse("fulfillment", "dubbo-order-1", 1));

        mockMvc.perform(post("/sample/dubbo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"dubbo-order-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocol").value("DUBBO"))
                .andExpect(jsonPath("$.orderNo").value("dubbo-order-1"))
                .andExpect(jsonPath("$.statementValue").value(1));
    }

    @Test
    void rejectsBlankOrderNumberBeforeCallingDubbo() throws Exception {
        mockMvc.perform(post("/sample/dubbo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsDubboFailureToBadGateway() throws Exception {
        when(client.echo("dubbo-order-1")).thenThrow(new IllegalStateException("offline"));

        mockMvc.perform(post("/sample/dubbo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"dubbo-order-1\"}"))
                .andExpect(status().isBadGateway());
    }
}
