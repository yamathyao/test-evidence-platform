package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.blackbox.BlackboxRequestState;
import net.bytebuddy.asm.Advice;

public final class ServletDispatchAdvice {
    private ServletDispatchAdvice() { }
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enter(@Advice.Argument(0) Object request) {
        try {
            String runId = header(request, "X-Test-Run-Id");
            TestContext context = HttpEvidenceRuntime.enter(runId, header(request, "X-Test-Case-Id"),
                    header(request, "X-Test-Profile-Id"), header(request, "X-Test-Profile-Version"),
                    header(request, "X-Test-Trace-Id"), header(request, "X-Test-Parent-Span-Id"),
                    "true".equals(header(request, "X-Test-Capture-Http-Payload")));
            if (context == null) context = BlackboxRequestState.begin(request);
            AgentDiagnostics.log("http.payload.inbound header=" + header(request, "X-Test-Capture-Http-Payload")
                    + " enabled=" + (context != null && context.payloadCaptureEnabled()));
            String contentType = header(request, "Content-Type");
            String contentEncoding = header(request, "Content-Encoding");
            if (context != null) HttpEvidenceRuntime.startPayload(context, value(request, "getMethod"), contentType, contentEncoding);
            else if (BlackboxRequestState.mayMatchPayloadJsonBody()) {
                HttpEvidenceRuntime.startDeferredPayload(contentType, contentEncoding);
            }
            return new State(context, value(request, "getMethod"), value(request, "getRequestURI"), System.currentTimeMillis());
        } catch (Throwable ignored) { return null; }
    }
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Argument(1) Object response, @Advice.Enter State state, @Advice.Thrown Throwable failure) {
        if (state == null) return;
        TestContext context = state.context == null ? TestContextHolder.currentOrNull() : state.context;
        try { HttpEvidenceRuntime.complete(context, state.method, state.target, Integer.valueOf(value(response, "getStatus")), state.startedAt, failure,
                value(response, "getContentType"), header(response, "Content-Encoding")); }
        catch (Throwable ignored) { HttpEvidenceRuntime.complete(context, state.method, state.target, null, state.startedAt, failure,
                value(response, "getContentType"), header(response, "Content-Encoding")); }
        finally { BlackboxRequestState.clear(); }
    }
    public static String header(Object request, String name) {
        try {
            Object value = request == null ? null : request.getClass().getMethod("getHeader", String.class).invoke(request, name);
            return value == null ? null : value.toString();
        } catch (Throwable ignored) { return null; }
    }
    public static String value(Object target, String method) {
        try { return target == null ? "" : String.valueOf(target.getClass().getMethod(method).invoke(target)); }
        catch (Throwable ignored) { return ""; }
    }
    public static final class State { public final TestContext context; public final String method; public final String target; public final long startedAt;
        public State(TestContext context, String method, String target, long startedAt) { this.context = context; this.method = method; this.target = target; this.startedAt = startedAt; } }
}
