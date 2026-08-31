package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Consumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public final class WebClientMonoAdvice {
    private WebClientMonoAdvice() { }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Argument(value = 0, readOnly = false, typing = Assigner.Typing.DYNAMIC) Object request) {
        request = copyRequest(request);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.Argument(0) Object request,
                            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object mono) {
        mono = wrap(request, mono);
    }

    public static Object copyRequest(Object request) {
        if (request == null || TestContextHolder.currentOrNull() == null) return request;
        try {
            Class<?> clientRequest = Class.forName("org.springframework.web.reactive.function.client.ClientRequest", false,
                    request.getClass().getClassLoader());
            Class<?> builderType = Class.forName("org.springframework.web.reactive.function.client.ClientRequest$Builder", false,
                    request.getClass().getClassLoader());
            Object builder = clientRequest.getMethod("from", clientRequest).invoke(null, request);
            builderType.getMethod("headers", Consumer.class).invoke(builder,
                    new HeaderConsumer(HttpClientPropagation.headers(TestContextHolder.currentOrNull())));
            return builderType.getMethod("build").invoke(builder);
        } catch (Throwable ignored) { return request; }
    }

    @SuppressWarnings("unchecked")
    public static <T> T wrap(Object request, T publisher) {
        if (publisher == null || !publisher.getClass().getName().startsWith("reactor.core.publisher.Mono")) return publisher;
        HttpClientEvidenceRuntime.ClientState state = HttpClientEvidenceRuntime.begin(method(request), target(request));
        if (state == null) return publisher;
        try {
            Object success = publisher.getClass().getMethod("doOnSuccess", Consumer.class)
                    .invoke(publisher, new SuccessConsumer(state));
            return (T) success.getClass().getMethod("doOnError", Consumer.class)
                    .invoke(success, new FailureConsumer(state));
        } catch (Throwable ignored) { return publisher; }
    }

    private static String method(Object request) { return value(request, "method"); }
    private static String target(Object request) { return value(request, "url"); }
    private static String value(Object target, String method) {
        try {
            if (target == null) return "";
            Method reflected = target.getClass().getMethod(method);
            reflected.setAccessible(true);
            Object value = reflected.invoke(target);
            return value == null ? "" : String.valueOf(value);
        }
        catch (Throwable ignored) { return ""; }
    }

    private static final class HeaderConsumer implements Consumer<Object> {
        private final Map<String, String> headers;

        private HeaderConsumer(Map<String, String> headers) { this.headers = headers; }

        @Override public void accept(Object headers) {
            for (Map.Entry<String, String> entry : this.headers.entrySet()) {
                try {
                    Method getFirst = headers.getClass().getMethod("getFirst", String.class);
                    if (getFirst.invoke(headers, entry.getKey()) == null) {
                        headers.getClass().getMethod("set", String.class, String.class)
                                .invoke(headers, entry.getKey(), entry.getValue());
                    }
                } catch (Throwable ignored) { }
            }
        }
    }

    private static final class SuccessConsumer implements Consumer<Object> {
        private final HttpClientEvidenceRuntime.ClientState state;
        private SuccessConsumer(HttpClientEvidenceRuntime.ClientState state) { this.state = state; }
        @Override public void accept(Object response) { HttpClientEvidenceRuntime.complete(state, status(response), null); }
    }

    private static final class FailureConsumer implements Consumer<Throwable> {
        private final HttpClientEvidenceRuntime.ClientState state;
        private FailureConsumer(HttpClientEvidenceRuntime.ClientState state) { this.state = state; }
        @Override public void accept(Throwable failure) { HttpClientEvidenceRuntime.complete(state, null, failure); }
    }

    private static Integer status(Object response) {
        try { return response == null ? null : Integer.valueOf(value(response, "rawStatusCode")); }
        catch (RuntimeException ignored) { return null; }
    }
}
