package com.talkanything.testevidence.sample.order.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolClientTest {
    private HttpServer server;
    private String baseUrl;
    private String requestPath;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/protocols/echo", exchange -> {
            requestPath = exchange.getRequestURI().toString();
            byte[] body = "{\"service\":\"fulfillment\",\"orderNo\":\"matrix-order-1\",\"statementValue\":1}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void invokesFulfillmentEchoWithEachClient() {
        for (ProtocolClient client : clients().toList()) {
            ProtocolEchoResponse response = client.invoke(new ProtocolInvocation("matrix-order-1"));

            assertEquals("fulfillment", response.service());
            assertEquals("matrix-order-1", response.orderNo());
            assertEquals(1, response.statementValue());
            assertEquals("/internal/protocols/echo?orderNo=matrix-order-1", requestPath);
        }
    }

    private Stream<ProtocolClient> clients() {
        ProtocolClientSupport support = new ProtocolClientSupport(baseUrl, new ObjectMapper());
        return Stream.of(
                new Apache4ProtocolClient(support),
                new Apache5ProtocolClient(support),
                new OkHttpProtocolClient(support),
                new FeignProtocolClient(support),
                new WebClientProtocolClient(support));
    }
}