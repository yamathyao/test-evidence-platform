package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class HttpClientPropagationTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void injectsMissingHeadersWithoutOverwritingCallerValues() {
        TestContext context = TestContextHolder.enter("run-1", "case-1", "profile-1", 7, "trace-1", "parent-1", true);
        Headers headers = new Headers();
        headers.set("X-Test-Trace-Id", "caller-trace");

        HttpClientPropagation.inject(headers, context);

        assertEquals("run-1", headers.getFirst("X-Test-Run-Id"));
        assertEquals("case-1", headers.getFirst("X-Test-Case-Id"));
        assertEquals("profile-1", headers.getFirst("X-Test-Profile-Id"));
        assertEquals("7", headers.getFirst("X-Test-Profile-Version"));
        assertEquals("caller-trace", headers.getFirst("X-Test-Trace-Id"));
        assertEquals(context.spanId(), headers.getFirst("X-Test-Parent-Span-Id"));
        assertEquals("true", headers.getFirst("X-Test-Capture-Http-Payload"));
    }

    @Test
    void leavesHeadersUntouchedWithoutContext() {
        Headers headers = new Headers();

        HttpClientPropagation.inject(headers, null);

        assertNull(headers.getFirst("X-Test-Run-Id"));
    }

    @Test
    void copiesImmutableRequestWithMissingHeaders() {
        ImmutableRequest original = new ImmutableRequest("caller-trace");
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-Test-Run-Id", "run-1");
        headers.put("X-Test-Trace-Id", "agent-trace");

        ImmutableRequest copied = (ImmutableRequest) HttpClientPropagation.copyWithHeaders(original, headers);

        assertEquals("run-1", copied.headers.get("X-Test-Run-Id"));
        assertEquals("caller-trace", copied.headers.get("X-Test-Trace-Id"));
        assertSame(original, HttpClientPropagation.copyWithHeaders(original, null));
    }

    static final class Headers {
        private final Map<String, String> values = new LinkedHashMap<String, String>();
        public String getFirst(String name) { return values.get(name); }
        public void set(String name, String value) { values.put(name, value); }
    }

    static final class ImmutableRequest {
        private final Map<String, String> headers;
        ImmutableRequest(String traceId) {
            this.headers = new LinkedHashMap<String, String>();
            this.headers.put("X-Test-Trace-Id", traceId);
        }
        private ImmutableRequest(Map<String, String> headers) { this.headers = headers; }
        public String header(String name) { return headers.get(name); }
        public Builder newBuilder() { return new Builder(headers); }
    }

    static final class Builder {
        private final Map<String, String> headers;
        Builder(Map<String, String> existing) { this.headers = new LinkedHashMap<String, String>(existing); }
        public Builder header(String name, String value) { headers.put(name, value); return this; }
        public ImmutableRequest build() { return new ImmutableRequest(headers); }
    }
}