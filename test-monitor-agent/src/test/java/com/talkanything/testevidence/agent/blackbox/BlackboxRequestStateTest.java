package com.talkanything.testevidence.agent.blackbox;

import com.sun.net.httpserver.HttpServer;
import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxRequestStateTest {
    @Test
    void matchesHttpHeaderWithoutCaseSensitivity() {
        BlackboxRequestValues values = new BlackboxRequestValues();
        values.put("HEADER", "x-tenant", "tenant-a");
        BlackboxRule rule = new BlackboxRule("rule", "run", "case", "profile", 1,
                System.currentTimeMillis() + 10000,
                java.util.Collections.singletonList(
                        new BlackboxRule.Matcher("X-Tenant", java.util.Collections.singletonList("HEADER"), "tenant-a")));

        assertTrue(new BlackboxValueMatcher().matchRequest(rule, values));
    }

    @Test
    void extractsScalarFieldsFromDeserializedBody() {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("orderNo", "ORD-1");
        Map<String, Object> nested = new HashMap<String, Object>();
        nested.put("tenantId", "tenant-a");
        body.put("payload", nested);

        BlackboxRequestValues values = BlackboxRequestState.bodyValues(body);
        BlackboxRule rule = new BlackboxRule("rule", "run", "case", "profile", 1, System.currentTimeMillis() + 10000,
                java.util.Arrays.asList(new BlackboxRule.Matcher("orderNo", java.util.Arrays.asList("JSON_BODY"), "ORD-1"),
                        new BlackboxRule.Matcher("tenantId", java.util.Arrays.asList("JSON_BODY"), "tenant-a")));

        assertTrue(new BlackboxValueMatcher().matchRequest(rule, values));
    }

    @Test
    void extractsFieldsFromRecordStyleAccessors() {
        BlackboxRequestValues values = BlackboxRequestState.bodyValues(new RecordStyleOrder("ORD-RECORD"));
        BlackboxRule rule = new BlackboxRule("rule", "run", "case", "profile", 1, System.currentTimeMillis() + 10000,
                java.util.Collections.singletonList(
                        new BlackboxRule.Matcher("orderNo", java.util.Collections.singletonList("JSON_BODY"), "ORD-RECORD")));

        assertTrue(new BlackboxValueMatcher().matchRequest(rule, values));
    }

    @Test
    void createsDifferentRootTracesForTwoIndependentMatchingRequests() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/blackbox-correlation-rules", exchange -> {
            byte[] response = ruleLine().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            BlackboxCorrelationRuntime.initialize("http://localhost:" + server.getAddress().getPort(), "order", "token");
            BlackboxCorrelationRuntime.current().refreshNow();
            TestContext first = BlackboxRequestState.begin(new QueryRequest());
            BlackboxRequestState.clear();
            TestContextHolder.clear();
            TestContext second = BlackboxRequestState.begin(new QueryRequest());

            assertNotEquals(first.traceId(), second.traceId());
        } finally {
            BlackboxRequestState.clear();
            TestContextHolder.clear();
            server.stop(0);
        }
    }

    private static String ruleLine() {
        return encoded("rule") + "\t" + encoded("run") + "\t" + encoded("case") + "\t" + encoded("profile")
                + "\t" + encoded("1") + "\t" + encoded(String.valueOf(System.currentTimeMillis() + 10000))
                + "\t" + encoded("browser") + "\t" + encoded("orderNo") + "\t" + encoded("[\"QUERY\"]")
                + "\t" + encoded("ORD-5");
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static final class QueryRequest {
        public String getRequestURI() { return "/orders"; }
        public String getQueryString() { return "orderNo=ORD-5"; }
    }

    static final class RecordStyleOrder {
        private final String orderNo;

        RecordStyleOrder(String orderNo) {
            this.orderNo = orderNo;
        }

        public String orderNo() {
            return orderNo;
        }
    }
}
