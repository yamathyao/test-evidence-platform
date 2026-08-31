package com.talkanything.testevidence.sample.order.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProtocolClientSupport {
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    public ProtocolClientSupport(@Value("${sample.fulfillment.base-url}") String baseUrl, ObjectMapper objectMapper) { this.baseUrl = baseUrl; this.objectMapper = objectMapper; }
    public String uri(ProtocolInvocation request) {
        if (request == null || request.orderNo() == null || request.orderNo().isBlank()) throw new IllegalArgumentException("Invalid order number");
        return baseUrl + "/internal/protocols/echo?orderNo=" + URLEncoder.encode(request.orderNo(), StandardCharsets.UTF_8);
    }
    public ProtocolEchoResponse read(int status, InputStream body) {
        if (status < 200 || status >= 300) throw new IllegalStateException("Unexpected fulfillment status: " + status);
        try { return objectMapper.readValue(body, ProtocolEchoResponse.class); } catch (IOException e) { throw new IllegalStateException("Cannot read fulfillment response", e); }
    }
}