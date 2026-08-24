package com.talkanything.testevidence.agent.blackbox;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxCorrelationRuntimeTest {
    @Test
    void logsRuleRefreshCountWhenDiagnosticsAreEnabled() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/blackbox-correlation-rules", new ProtocolHandler(new AtomicBoolean(false)));
        server.start();
        String property = "test.monitor.agent.debug";
        String previous = System.getProperty(property);
        java.io.PrintStream original = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setProperty(property, "true");
            System.setErr(new java.io.PrintStream(output));
            new BlackboxCorrelationRuntime("http://localhost:" + server.getAddress().getPort(), "order", "token").refreshNow();
            assertTrue(new String(output.toByteArray(), StandardCharsets.UTF_8)
                    .contains("blackbox.rule-refresh.service=order count=1"));
        } finally {
            System.setErr(original);
            if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
            server.stop(0);
        }
    }

    @Test
    void preservesUnexpiredRulesWhenRefreshFails() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(false);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/blackbox-correlation-rules", new ProtocolHandler(fail));
        server.start();
        try {
            BlackboxCorrelationRuntime runtime = new BlackboxCorrelationRuntime(
                    "http://localhost:" + server.getAddress().getPort(), "order-service", "token");
            runtime.refreshNow();
            fail.set(true);
            runtime.refreshNow();

            assertNotNull(runtime.match(values("orderNo", "ORD-1")));
        } finally {
            server.stop(0);
        }
    }

    private Map<String, String> values(String key, String value) {
        Map<String, String> values = new HashMap<String, String>();
        values.put(key, value);
        return values;
    }

    private static final class ProtocolHandler implements HttpHandler {
        private final AtomicBoolean fail;
        private ProtocolHandler(AtomicBoolean fail) { this.fail = fail; }

        @Override
        public void handle(HttpExchange exchange) {
            try {
                byte[] response = fail.get() ? new byte[0] : line().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(fail.get() ? 500 : 200, response.length);
                OutputStream output = exchange.getResponseBody();
                try { output.write(response); } finally { output.close(); }
            } catch (Exception ignored) { }
        }

        private String line() {
            return encoded("rule") + "\t" + encoded("run") + "\t" + encoded("case") + "\t" + encoded("profile")
                    + "\t" + encoded("1") + "\t" + encoded(String.valueOf(System.currentTimeMillis() + 10000))
                    + "\t" + encoded("order") + "\t" + encoded("orderNo") + "\t" + encoded("[\"QUERY\"]")
                    + "\t" + encoded("ORD-1");
        }

        private String encoded(String value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
