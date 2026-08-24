package com.talkanything.testevidence.sample.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Component
public class FulfillmentClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public FulfillmentClient(RestTemplate restTemplate,
                             @Value("${sample.fulfillment.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public FulfillmentResponse fulfill(OrderRequest order) {
        return restTemplate.postForObject(baseUrl + "/internal/fulfillments", order, FulfillmentResponse.class);
    }

    public Optional<FulfillmentResponse> find(String orderNo) {
        try {
            return Optional.ofNullable(restTemplate.getForObject(
                    baseUrl + "/internal/fulfillments/{orderNo}", FulfillmentResponse.class, orderNo));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        }
    }
}
