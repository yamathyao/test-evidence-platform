package com.talkanything.testevidence.sample.order;

import com.talkanything.testevidence.sample.order.protocol.ProtocolClient;
import com.talkanything.testevidence.sample.order.protocol.ProtocolClientRegistry;
import com.talkanything.testevidence.sample.order.protocol.ProtocolEchoResponse;
import com.talkanything.testevidence.sample.order.protocol.ProtocolInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProtocolController.class)
class ProtocolControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private ProtocolClientRegistry registry;

    @Test
    void dispatchesProtocolRequest() throws Exception {
        ProtocolClient client = mock(ProtocolClient.class);
        when(registry.require("okhttp")).thenReturn(client);
        when(client.invoke(new ProtocolInvocation("matrix-order-1")))
                .thenReturn(new ProtocolEchoResponse("fulfillment", "matrix-order-1", 1));

        mockMvc.perform(post("/sample/protocols/okhttp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"matrix-order-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client").value("okhttp"))
                .andExpect(jsonPath("$.statementValue").value(1));
    }

    @Test
    void rejectsBlankOrderNumber() throws Exception {
        mockMvc.perform(post("/sample/protocols/okhttp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsDownstreamFailureToBadGateway() throws Exception {
        ProtocolClient client = mock(ProtocolClient.class);
        when(registry.require("okhttp")).thenReturn(client);
        when(client.invoke(new ProtocolInvocation("matrix-order-1")))
                .thenThrow(new IllegalStateException("offline"));

        mockMvc.perform(post("/sample/protocols/okhttp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"matrix-order-1\"}"))
                .andExpect(status().isBadGateway());
    }
}