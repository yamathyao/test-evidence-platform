package com.talkanything.testevidence.sample.order;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FulfillmentClientTest {
    @Test
    void postsDetailedOrderToFulfillmentEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        FulfillmentClient client = new FulfillmentClient(restTemplate, "http://fulfillment");
        server.expect(requestTo("http://fulfillment/internal/fulfillments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"orderNo\":\"P20-ORDER-1\",\"sku\":\"SKU-COFFEE\",\"quantity\":2,\"note\":\"blackbox\",\"status\":\"FULFILLED\",\"fulfilledAt\":\"2026-08-19T15:30:00Z\"}", MediaType.APPLICATION_JSON));

        assertEquals("FULFILLED", client.fulfill(new OrderRequest("P20-ORDER-1", "SKU-COFFEE", 2, "blackbox")).status());

        server.verify();
    }
}
