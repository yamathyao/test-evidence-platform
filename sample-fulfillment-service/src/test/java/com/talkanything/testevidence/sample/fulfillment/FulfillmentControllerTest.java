package com.talkanything.testevidence.sample.fulfillment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FulfillmentController.class)
class FulfillmentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FulfillmentService fulfillmentService;

    @Test
    void fulfillsValidOrder() throws Exception {
        when(fulfillmentService.fulfill(new FulfillmentRequest("P20-ORDER-1", "SKU-COFFEE", 1, "")))
                .thenReturn(record("P20-ORDER-1", "SKU-COFFEE", 1, ""));

        mockMvc.perform(post("/internal/fulfillments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"P20-ORDER-1\",\"sku\":\"SKU-COFFEE\",\"quantity\":1,\"note\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));

        verify(fulfillmentService).fulfill(new FulfillmentRequest("P20-ORDER-1", "SKU-COFFEE", 1, ""));
    }

    @Test
    void createsAndReadsDetailedFulfillment() throws Exception {
        when(fulfillmentService.fulfill(new FulfillmentRequest("BROWSER-001", "SKU-COFFEE", 2, "blackbox")))
                .thenReturn(record("BROWSER-001", "SKU-COFFEE", 2, "blackbox"));
        when(fulfillmentService.find("BROWSER-001"))
                .thenReturn(java.util.Optional.of(record("BROWSER-001", "SKU-COFFEE", 2, "blackbox")));
        mockMvc.perform(post("/internal/fulfillments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"BROWSER-001\",\"sku\":\"SKU-COFFEE\",\"quantity\":2,\"note\":\"blackbox\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));
        mockMvc.perform(get("/internal/fulfillments/BROWSER-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void rejectsBlankOrderNumber() throws Exception {
        when(fulfillmentService.fulfill(new FulfillmentRequest("   ", "SKU-COFFEE", 1, null)))
                .thenThrow(new IllegalArgumentException("Invalid order number"));

        mockMvc.perform(post("/internal/fulfillments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"   \",\"sku\":\"SKU-COFFEE\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesFulfillmentForOrder() throws Exception {
        mockMvc.perform(delete("/internal/fulfillments/P20-ORDER-3"))
                .andExpect(status().isNoContent());

        verify(fulfillmentService).delete("P20-ORDER-3");
    }

    @Test
    void returnsNotFoundForUnknownFulfillment() throws Exception {
        when(fulfillmentService.find("P20-UNKNOWN")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/internal/fulfillments/P20-UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    private FulfillmentRecord record(String orderNo, String sku, int quantity, String note) {
        return new FulfillmentRecord(orderNo, sku, quantity, note, "FULFILLED",
                java.time.Instant.parse("2026-08-19T15:30:00Z"));
    }
}
