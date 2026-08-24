package com.talkanything.testevidence.agent.http;

import com.sun.net.httpserver.HttpServer;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestTemplateInstrumentationTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void injectsTestHeadersIntoActualRestTemplateRequest() throws Exception {
        Map<String, String> headers = new ConcurrentHashMap<String, String>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/downstream", exchange -> {
            copyHeader(exchange, headers, "X-Test-Run-Id");
            copyHeader(exchange, headers, "X-Test-Case-Id");
            copyHeader(exchange, headers, "X-Test-Profile-Id");
            copyHeader(exchange, headers, "X-Test-Profile-Version");
            copyHeader(exchange, headers, "X-Test-Trace-Id");
            copyHeader(exchange, headers, "X-Test-Parent-Span-Id");
            copyHeader(exchange, headers, "X-Test-Capture-Http-Payload");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            Class.forName("org.springframework.web.client.RestTemplate");
            TransformListener listener = new TransformListener();
            HttpInstrumentation.install(ByteBuddyAgent.install(), listener);
            TestContextHolder.enter("run-1", "case-1", "profile-1", 2, "trace-1", "parent-1", true);
            String currentSpanId = TestContextHolder.current().get().spanId();

            new RestTemplate().execute("http://localhost:" + server.getAddress().getPort() + "/downstream",
                    HttpMethod.GET, request -> { }, response -> null);

            assertTrue(listener.restTemplateTransformed.get(), listener.error.get());
            assertEquals("run-1", headers.get("X-Test-Run-Id"));
            assertEquals("case-1", headers.get("X-Test-Case-Id"));
            assertEquals("profile-1", headers.get("X-Test-Profile-Id"));
            assertEquals("2", headers.get("X-Test-Profile-Version"));
            assertEquals("trace-1", headers.get("X-Test-Trace-Id"));
            assertEquals(currentSpanId, headers.get("X-Test-Parent-Span-Id"));
            assertEquals("true", headers.get("X-Test-Capture-Http-Payload"));
        } finally {
            server.stop(0);
        }
    }

    private static void copyHeader(com.sun.net.httpserver.HttpExchange exchange, Map<String, String> headers, String name) {
        headers.put(name, exchange.getRequestHeaders().getFirst(name));
    }

    private static final class TransformListener extends AgentBuilder.Listener.Adapter {
        private final AtomicReference<String> error = new AtomicReference<String>();
        private final java.util.concurrent.atomic.AtomicBoolean restTemplateTransformed = new java.util.concurrent.atomic.AtomicBoolean();

        @Override
        public void onTransformation(TypeDescription type, ClassLoader loader, JavaModule module,
                                     boolean loaded, DynamicType dynamicType) {
            if ("org.springframework.web.client.RestTemplate".equals(type.getName())) restTemplateTransformed.set(true);
        }

        @Override
        public void onError(String typeName, ClassLoader loader, JavaModule module, boolean loaded, Throwable throwable) {
            if ("org.springframework.web.client.RestTemplate".equals(typeName)) error.set(throwable.toString());
        }
    }
}
