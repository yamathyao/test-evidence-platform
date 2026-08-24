package com.talkanything.testevidence.sample.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FulfillmentClient fulfillmentClient;

    @Test
    void processesOrderWhenFulfillmentSucceeds() throws Exception {
        OrderRequest request = new OrderRequest("P20-ORDER-1", "SKU-COFFEE", 2, "blackbox");
        when(fulfillmentClient.fulfill(request)).thenReturn(response("P20-ORDER-1"));

        mockMvc.perform(post("/sample/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"P20-ORDER-1\",\"sku\":\"SKU-COFFEE\",\"quantity\":2,\"note\":\"blackbox\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(true))
                .andExpect(jsonPath("$.status").value("FULFILLED"));
        verify(fulfillmentClient).fulfill(request);
    }

    @Test
    void rejectsBlankOrderNumber() throws Exception {
        mockMvc.perform(post("/sample/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\" \",\"sku\":\"SKU-COFFEE\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadGatewayWhenFulfillmentFails() throws Exception {
        when(fulfillmentClient.fulfill(new OrderRequest("P20-ORDER-2", "SKU-COFFEE", 1, "")))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("offline"));

        mockMvc.perform(post("/sample/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"P20-ORDER-2\",\"sku\":\"SKU-COFFEE\",\"quantity\":1}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void proxiesFulfillmentStatusForExistingOrder() throws Exception {
        when(fulfillmentClient.find("P20-ORDER-3")).thenReturn(java.util.Optional.of(response("P20-ORDER-3")));

        mockMvc.perform(get("/sample/orders/P20-ORDER-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value("P20-ORDER-3"))
                .andExpect(jsonPath("$.status").value("FULFILLED"));
    }

    private FulfillmentResponse response(String orderNo) {
        return new FulfillmentResponse(orderNo, "SKU-COFFEE", 1, "", "FULFILLED",
                java.time.Instant.parse("2026-08-19T15:30:00Z"));
    }
}
