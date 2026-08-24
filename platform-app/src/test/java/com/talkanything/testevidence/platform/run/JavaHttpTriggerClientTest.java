package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaHttpTriggerClientTest {
    @Test
    void addsProfileIdentityToTriggerHeaders() throws Exception {
        AtomicReference<String> profileId = new AtomicReference<String>();
        AtomicReference<String> captureEnabled = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/orders", exchange -> {
            profileId.set(exchange.getRequestHeaders().getFirst("X-Test-Profile-Id"));
            captureEnabled.set(exchange.getRequestHeaders().getFirst("X-Test-Capture-Http-Payload"));
            byte[] responseBody = "{\"data\":{\"orderNo\":\"ORD-001\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        try {
            UUID expectedProfileId = UUID.randomUUID();
            String config = "{\"method\":\"GET\",\"url\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/orders\"}";
            HttpTriggerResponse response = new JavaHttpTriggerClient().execute(new ObjectMapper().readTree(config),
                    new HttpTriggerClient.TriggerContext(UUID.randomUUID(), UUID.randomUUID(), expectedProfileId, 1, 5, true));

            assertEquals(201, response.statusCode());
            assertEquals("{\"data\":{\"orderNo\":\"ORD-001\"}}", response.body());
            assertEquals(expectedProfileId.toString(), profileId.get());
            assertEquals("true", captureEnabled.get());
        } finally {
            server.stop(0);
        }
    }
}
