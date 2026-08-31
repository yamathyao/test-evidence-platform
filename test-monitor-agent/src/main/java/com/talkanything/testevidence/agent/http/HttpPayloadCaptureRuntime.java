package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class HttpPayloadCaptureRuntime {
    private static final int MAX_BYTES = 65_536;
    private final ThreadLocal<State> current = new ThreadLocal<State>();

    public void start(TestContext context, String requestContentType, String requestContentEncoding) {
        start(context, null, requestContentType, requestContentEncoding);
    }

    public void start(TestContext context, String requestMethod, String requestContentType, String requestContentEncoding) {
        if (context == null || !context.payloadCaptureEnabled()) {
            current.remove();
            return;
        }
        State state = current.get();
        if (state == null) {
            state = new State(requestMethod, requestContentType, requestContentEncoding);
            current.set(state);
        }
        state.activate();
    }

    public void startDeferred(String requestContentType, String requestContentEncoding) {
        if (current.get() == null) current.set(new State(null, requestContentType, requestContentEncoding));
    }

    public void activate(TestContext context) {
        if (context != null) start(context, null, null);
    }

    public void request(byte[] bytes, int offset, int length) { append(current.get(), true, bytes, offset, length); }
    public void response(byte[] bytes, int offset, int length) { append(current.get(), false, bytes, offset, length); }

    public HttpPayload finish(String responseContentType, String responseContentEncoding) {
        State state = current.get();
        current.remove();
        if (state == null || !state.captureEnabled) return null;
        return new HttpPayload(state.requestContentType, responseContentType,
                state.requestBodyNotApplicable ? "NOT_APPLICABLE" : status(state.requestContentType, state.requestContentEncoding),
                status(responseContentType, responseContentEncoding),
                state.requestBodyNotApplicable ? null : body(state.request, state.requestContentType, state.requestContentEncoding),
                body(state.response, responseContentType, responseContentEncoding),
                state.requestTruncated, state.responseTruncated);
    }

    private void append(State state, boolean request, byte[] bytes, int offset, int length) {
        if (state == null || (!request && !state.captureEnabled) || bytes == null || length <= 0
                || offset < 0 || offset + length > bytes.length) return;
        ByteArrayOutputStream target = request ? state.request : state.response;
        int remaining = MAX_BYTES - target.size();
        if (remaining <= 0) {
            if (request) state.requestTruncated = true; else state.responseTruncated = true;
            return;
        }
        int accepted = Math.min(remaining, length);
        target.write(bytes, offset, accepted);
        if (accepted < length) {
            if (request) state.requestTruncated = true; else state.responseTruncated = true;
        }
    }

    private String status(String contentType, String encoding) { return supported(contentType, encoding) ? "CAPTURED" : "UNSUPPORTED"; }
    private String body(ByteArrayOutputStream buffer, String contentType, String encoding) {
        return supported(contentType, encoding) ? new String(buffer.toByteArray(), StandardCharsets.UTF_8) : null;
    }
    private boolean supported(String contentType, String encoding) {
        if (encoding != null && !encoding.trim().isEmpty() && !"identity".equalsIgnoreCase(encoding)) return false;
        String value = contentType == null ? "" : contentType.toLowerCase();
        return value.startsWith("text/") || value.contains("application/json") || value.contains("+json")
                || value.contains("application/xml") || value.contains("+xml");
    }

    private static final class State {
        private final boolean requestBodyNotApplicable;
        private final String requestContentType;
        private final String requestContentEncoding;
        private final ByteArrayOutputStream request = new ByteArrayOutputStream();
        private final ByteArrayOutputStream response = new ByteArrayOutputStream();
        private boolean captureEnabled;
        private boolean requestTruncated;
        private boolean responseTruncated;
        private State(String requestMethod, String requestContentType, String requestContentEncoding) {
            this.requestBodyNotApplicable = "GET".equalsIgnoreCase(requestMethod) || "HEAD".equalsIgnoreCase(requestMethod);
            this.requestContentType = requestContentType;
            this.requestContentEncoding = requestContentEncoding;
        }
        private void activate() { captureEnabled = true; }
    }

    public static final class HttpPayload {
        private final String requestContentType;
        private final String responseContentType;
        private final String requestStatus;
        private final String responseStatus;
        private final String requestBody;
        private final String responseBody;
        private final boolean requestTruncated;
        private final boolean responseTruncated;
        private HttpPayload(String requestContentType, String responseContentType, String requestStatus, String responseStatus,
                            String requestBody, String responseBody, boolean requestTruncated, boolean responseTruncated) {
            this.requestContentType = requestContentType; this.responseContentType = responseContentType;
            this.requestStatus = requestStatus; this.responseStatus = responseStatus;
            this.requestBody = requestBody; this.responseBody = responseBody;
            this.requestTruncated = requestTruncated; this.responseTruncated = responseTruncated;
        }
        public String requestContentType() { return requestContentType; }
        public String responseContentType() { return responseContentType; }
        public String requestStatus() { return requestStatus; }
        public String responseStatus() { return responseStatus; }
        public String requestBody() { return requestBody; }
        public String responseBody() { return responseBody; }
        public boolean requestTruncated() { return requestTruncated; }
        public boolean responseTruncated() { return responseTruncated; }
    }
}
