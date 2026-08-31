package com.talkanything.testevidence.agent;

import com.talkanything.testevidence.agent.http.HttpClientEvidenceRuntime;
import com.talkanything.testevidence.agent.http.OkHttpNewCallAdvice;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import feign.Feign;
import feign.RequestLine;
import net.bytebuddy.agent.ByteBuddyAgent;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestMonitorAgentTest {
    @Test
    void premainInitializesHttpClientEvidenceRuntime() throws Exception {
        resetHttpClientEvidenceRuntime();
        Instrumentation instrumentation = ByteBuddyAgent.install();

        TestMonitorAgent.premain("collector=http://localhost:8080,service=order-service,token=test-token", instrumentation);

        assertEquals("order-service", field("serviceName").get(null));
        assertNotNull(field("reporter").get(null));
    }

    @Test
    void premainReportsEvidenceForActualApacheHttpClientRequest() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        ByteArrayOutputStream evidence = new ByteArrayOutputStream();
        HttpServer server = server(delivered, evidence);
        server.start();
        try {
            Instrumentation instrumentation = ByteBuddyAgent.install();
            TestMonitorAgent.premain(arguments(server), instrumentation);
            TestContextHolder.enter("run-1", "case-1", "profile-1", 1);

            try (CloseableHttpClient client = HttpClients.createDefault();
                 CloseableHttpResponse response = client.execute(new HttpGet(url(server) + "/probe"))) {
                assertEquals(200, response.getStatusLine().getStatusCode());
            }

            assertEquals(true, delivered.await(5, TimeUnit.SECONDS));
            assertTrue(body(evidence).contains("\"protocol\":\"HTTP\",\"direction\":\"CLIENT\""));
        } finally {
            TestContextHolder.clear();
            server.stop(0);
        }
    }

    @Test
    void premainReportsEvidenceForActualApacheHttpClient5Request() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        ByteArrayOutputStream evidence = new ByteArrayOutputStream();
        HttpServer server = server(delivered, evidence);
        server.start();
        try {
            TestMonitorAgent.premain(arguments(server), ByteBuddyAgent.install());
            TestContextHolder.enter("run-1", "case-1", "profile-1", 1);

            try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient client =
                         org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {
                int status = client.execute(new org.apache.hc.client5.http.classic.methods.HttpGet(url(server) + "/probe"),
                        response -> response.getCode());
                assertEquals(200, status);
            }

            assertTrue(delivered.await(5, TimeUnit.SECONDS));
            assertTrue(body(evidence).contains("\"protocol\":\"HTTP\",\"direction\":\"CLIENT\""));
            assertTrue(body(evidence).contains("\"target\":\"/probe\""), body(evidence));
        } finally {
            TestContextHolder.clear();
            server.stop(0);
        }
    }

    @Test
    void premainReportsEvidenceForActualFeignRequest() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        ByteArrayOutputStream evidence = new ByteArrayOutputStream();
        HttpServer server = server(delivered, evidence);
        server.start();
        try {
            TestMonitorAgent.premain(arguments(server), ByteBuddyAgent.install());
            TestContextHolder.enter("run-1", "case-1", "profile-1", 1);

            ProbeApi api = Feign.builder().target(ProbeApi.class, url(server));
            try (feign.Response response = api.probe()) {
                assertEquals(200, response.status());
            }

            assertTrue(delivered.await(5, TimeUnit.SECONDS));
            assertTrue(body(evidence).contains("\"protocol\":\"HTTP\",\"direction\":\"CLIENT\""));
        } finally {
            TestContextHolder.clear();
            server.stop(0);
        }
    }

    @Test
    @Order(1)
    void premainReportsOneClientEvidenceForEachSupportedHttpClient() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        ByteArrayOutputStream evidence = new ByteArrayOutputStream();
        Map<String, String> requestRunIds = new ConcurrentHashMap<String, String>();
        HttpServer server = server(delivered, evidence, requestRunIds);
        server.start();
        try {
            Instrumentation instrumentation = ByteBuddyAgent.install();
            assertFalse(loaded(instrumentation, "org.apache.http.impl.client.CloseableHttpClient"));
            TestMonitorAgent.premain(arguments(server), instrumentation);
            TestContextHolder.enter("run-1", "case-1", "profile-1", 1);
            invokeApache4(server);
            invokeApache5(server);
            invokeOkHttp(server);
            invokeFeign(server);
            webClient().get().uri(url(server) + "/probe?client=webclient").exchange().block();

            assertTrue(delivered.await(5, TimeUnit.SECONDS));
            assertTrue(awaitClientEvidenceCount(evidence, 5));
            String deliveredEvidence = body(evidence);
            assertEquals(5, clientEvidenceCount(deliveredEvidence), deliveredEvidence);
            assertFalse(deliveredEvidence.contains("\"target\":\"\""), deliveredEvidence);
            assertEquals("run-1", requestRunIds.get("apache4"));
            assertEquals("run-1", requestRunIds.get("apache5"));
            assertEquals("run-1", requestRunIds.get("okhttp"));
            assertEquals("run-1", requestRunIds.get("feign"));
        } finally {
            TestContextHolder.clear();
            server.stop(0);
        }
    }

    @Test
    void premainReportsEvidenceForActualOkHttpRequest() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        ByteArrayOutputStream evidence = new ByteArrayOutputStream();
        HttpServer server = server(delivered, evidence);
        server.start();
        try {
            TestMonitorAgent.premain(arguments(server), ByteBuddyAgent.install());
            TestContextHolder.enter("run-1", "case-1", "profile-1", 1);

            try (Response response = new OkHttpClient().newCall(new Request.Builder().url(url(server) + "/probe").build()).execute()) {
                assertEquals(200, response.code());
            }

            assertTrue(delivered.await(5, TimeUnit.SECONDS));
            assertTrue(body(evidence).contains("\"protocol\":\"HTTP\",\"direction\":\"CLIENT\""));
        } finally {
            TestContextHolder.clear();
            server.stop(0);
        }
    }

    @Test
    void premainReportsEvidenceForActualWebClientRequest() throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        ByteArrayOutputStream evidence = new ByteArrayOutputStream();
        HttpServer server = server(delivered, evidence);
        server.start();
        try {
            TestMonitorAgent.premain(arguments(server), ByteBuddyAgent.install());
            TestContextHolder.enter("run-1", "case-1", "profile-1", 1);

            int status = webClient().get().uri(url(server) + "/probe").exchange()
                    .map(response -> response.rawStatusCode()).block();

            assertEquals(200, status);
            assertTrue(delivered.await(5, TimeUnit.SECONDS));
            assertTrue(body(evidence).contains("\"protocol\":\"HTTP\",\"direction\":\"CLIENT\""));
        } finally {
            TestContextHolder.clear();
            server.stop(0);
        }
    }

    private HttpServer server(CountDownLatch delivered, ByteArrayOutputStream evidence) throws Exception {
        return server(delivered, evidence, new ConcurrentHashMap<String, String>());
    }

    private HttpServer server(CountDownLatch delivered, ByteArrayOutputStream evidence, Map<String, String> requestRunIds) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/probe", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.startsWith("client=")) {
                String runId = exchange.getRequestHeaders().getFirst("X-Test-Run-Id");
                if (runId != null) requestRunIds.put(query.substring("client=".length()), runId);
            }
            respond(exchange, 200, new byte[0]);
        });
        server.createContext("/internal/v1/evidence/batches", exchange -> {
            copy(exchange.getRequestBody(), evidence);
            delivered.countDown();
            respond(exchange, 202, new byte[0]);
        });
        return server;
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws java.io.IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().close();
    }

    private void copy(InputStream input, ByteArrayOutputStream output) throws java.io.IOException {
        byte[] buffer = new byte[1024];
        for (int read; (read = input.read(buffer)) != -1;) output.write(buffer, 0, read);
    }

    private String arguments(HttpServer server) { return "collector=" + url(server) + ",service=order-service,token=test-token"; }
    private String url(HttpServer server) { return "http://localhost:" + server.getAddress().getPort(); }
    private String body(ByteArrayOutputStream value) { return new String(value.toByteArray(), StandardCharsets.UTF_8); }
    private boolean awaitClientEvidenceCount(ByteArrayOutputStream evidence, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (clientEvidenceCount(body(evidence)) >= expected) return true;
            Thread.sleep(20);
        }
        return false;
    }
    private int clientEvidenceCount(String value) {
        int count = 0;
        for (int index = value.indexOf("\"direction\":\"CLIENT\""); index >= 0;
             index = value.indexOf("\"direction\":\"CLIENT\"", index + 1)) count++;
        return count;
    }
    private void invokeApache4(HttpServer server) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(new HttpGet(url(server) + "/probe?client=apache4"))) {
            assertEquals(200, response.getStatusLine().getStatusCode());
        }
    }
    private void invokeApache5(HttpServer server) throws Exception {
        try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient client =
                     org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {
            int status = client.execute(new org.apache.hc.client5.http.classic.methods.HttpGet(
                    url(server) + "/probe?client=apache5"), response -> response.getCode());
            assertEquals(200, status);
        }
    }
    private void invokeOkHttp(HttpServer server) throws Exception {
        Request request = new Request.Builder().url(url(server) + "/probe?client=okhttp").build();
        okhttp3.Call call = new OkHttpClient().newCall(request);
        assertNotNull(OkHttpNewCallAdvice.requestFor(call));
        try (Response response = call.execute()) {
            assertEquals(200, response.code());
        }
    }
    private void invokeFeign(HttpServer server) {
        try (feign.Response response = Feign.builder().target(ProbeApi.class, url(server)).probe()) {
            assertEquals(200, response.status());
        }
    }
    private boolean loaded(Instrumentation instrumentation, String name) {
        for (Class<?> type : instrumentation.getAllLoadedClasses()) if (name.equals(type.getName())) return true;
        return false;
    }

    private WebClient webClient() {
        return WebClient.builder().clientConnector((method, uri, callback) -> Mono.just(new ClientHttpResponse() {
            @Override public HttpStatus getStatusCode() { return HttpStatus.OK; }
            @Override public int getRawStatusCode() { return 200; }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
            @Override public Flux<DataBuffer> getBody() { return Flux.empty(); }
            @Override public MultiValueMap<String, ResponseCookie> getCookies() { return new LinkedMultiValueMap<String, ResponseCookie>(); }
        })).build();
    }

    private void resetHttpClientEvidenceRuntime() throws Exception {
        field("serviceName").set(null, null);
        field("reporter").set(null, null);
    }

    private Field field(String name) throws Exception {
        Field field = HttpClientEvidenceRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    interface ProbeApi {
        @RequestLine("GET /probe?client=feign")
        feign.Response probe();
    }
}
