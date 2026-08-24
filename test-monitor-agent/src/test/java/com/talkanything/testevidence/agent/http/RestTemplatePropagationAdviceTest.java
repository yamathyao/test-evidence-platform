package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestTemplatePropagationAdviceTest {
    @AfterEach
    void clearContext() { TestContextHolder.clear(); }

    @Test
    void addsMissingTestAndTraceHeadersBeforeOriginalCallback() throws Exception {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 7, "trace-1", "parent-1");
        String currentSpanId = TestContextHolder.current().get().spanId();
        Headers headers = new Headers();
        RequestCallback original = request -> request.headers().set("X-Original-Callback", "called");

        ((RequestCallback) RestTemplatePropagationAdvice.wrap(original)).doWithRequest(new Request(headers));

        assertEquals("run-1", headers.getFirst("X-Test-Run-Id"));
        assertEquals("case-1", headers.getFirst("X-Test-Case-Id"));
        assertEquals("profile-1", headers.getFirst("X-Test-Profile-Id"));
        assertEquals("7", headers.getFirst("X-Test-Profile-Version"));
        assertEquals("trace-1", headers.getFirst("X-Test-Trace-Id"));
        assertEquals(currentSpanId, headers.getFirst("X-Test-Parent-Span-Id"));
        assertEquals("called", headers.getFirst("X-Original-Callback"));
    }

    @Test
    void preservesExistingRunHeaderAndLeavesCallbackWhenNoContext() {
        RequestCallback callback = request -> { };
        assertSame(callback, RestTemplatePropagationAdvice.wrap(callback));

        TestContextHolder.enter("run-new", "case-1", "profile-1", 1, "trace-1", "parent-1");
        Headers headers = new Headers();
        headers.set("X-Test-Run-Id", "run-existing");
        try {
            ((RequestCallback) RestTemplatePropagationAdvice.wrap(callback)).doWithRequest(new Request(headers));
        } catch (Exception exception) { throw new AssertionError(exception); }
        assertEquals("run-existing", headers.getFirst("X-Test-Run-Id"));
    }

    @Test
    void preservesOriginalCallbackException() {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 1, "trace-1", "parent-1");
        IOException expected = new IOException("callback failure");
        RequestCallback callback = request -> { throw expected; };

        IOException actual = assertThrows(IOException.class,
                () -> ((RequestCallback) RestTemplatePropagationAdvice.wrap(callback)).doWithRequest(new Request(new Headers())));

        assertSame(expected, actual);
    }

    @Test
    void wrapsCallbackThatInheritsRequestCallbackFromSuperclass() throws Exception {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 1, "trace-1", "parent-1");
        Headers headers = new Headers();

        ((RequestCallback) RestTemplatePropagationAdvice.wrap(new InheritedCallback())).doWithRequest(new Request(headers));

        assertEquals("run-1", headers.getFirst("X-Test-Run-Id"));
    }

    @Test
    void propagatesPayloadCaptureHeaderOnlyWhenEnabled() throws Exception {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 1, "trace-1", "parent-1", true);
        Headers enabled = new Headers();
        RequestCallback callback = request -> { };
        ((RequestCallback) RestTemplatePropagationAdvice.wrap(callback)).doWithRequest(new Request(enabled));
        assertEquals("true", enabled.getFirst("X-Test-Capture-Http-Payload"));

        TestContextHolder.enter("run-2", "case-2", "profile-2", 1, "trace-2", "parent-2", false);
        Headers disabled = new Headers();
        ((RequestCallback) RestTemplatePropagationAdvice.wrap(callback)).doWithRequest(new Request(disabled));
        assertEquals(null, disabled.getFirst("X-Test-Capture-Http-Payload"));
    }

    interface RequestCallback { void doWithRequest(Request request) throws Exception; }
    interface ClientRequest { Headers headers(); }
    static final class Request implements ClientRequest {
        private final Headers headers;
        Request(Headers headers) { this.headers = headers; }
        public Headers headers() { return headers; }
        public Headers getHeaders() { return headers; }
    }
    static final class Headers {
        private final Map<String, String> values = new HashMap<String, String>();
        public String getFirst(String name) { return values.get(name); }
        public void set(String name, String value) { values.put(name, value); }
    }
    static class CallbackBase implements RequestCallback {
        @Override
        public void doWithRequest(Request request) { }
    }
    static final class InheritedCallback extends CallbackBase { }
}
