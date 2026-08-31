package com.talkanything.testevidence.agent.feign;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.http.HttpClientEvidenceRuntime;
import com.talkanything.testevidence.agent.http.HttpClientPropagation;
import java.util.LinkedHashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public final class FeignExecutionAdvice {
    private FeignExecutionAdvice() { }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ExecutionState enter(@Advice.AllArguments Object[] arguments) {
        Object request = arguments == null || arguments.length == 0 ? null : arguments[0];
        injectHeaders(request);
        return new ExecutionState(HttpClientEvidenceRuntime.begin(value(request, "method"), value(request, "url")));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ExecutionState state,
                            @Advice.Return(readOnly = true, typing = Assigner.Typing.DYNAMIC) Object response,
                            @Advice.Thrown Throwable failure) {
        HttpClientEvidenceRuntime.complete(state == null ? null : state.clientState, status(response), failure);
    }

    public static void injectHeaders(Object request) {
        Object headers = invoke(request, "headers");
        if (!(headers instanceof Map) || TestContextHolder.currentOrNull() == null) return;
        Map existing = (Map) headers;
        for (Map.Entry<String, String> entry : HttpClientPropagation.headers(TestContextHolder.currentOrNull()).entrySet()) {
            if (existing.containsKey(entry.getKey())) continue;
            if (!putHeader(request, entry)) {
                existing = writableHeaders(request, existing);
                if (existing == null || !putHeader(request, entry)) return;
            }
        }
    }

    private static boolean putHeader(Object request, Map.Entry<String, String> entry) {
        try {
            request.getClass().getMethod("header", String.class, String.class)
                    .invoke(request, entry.getKey(), entry.getValue());
            return true;
        } catch (Throwable ignored) { return false; }
    }

    private static Map writableHeaders(Object request, Map existing) {
        try {
            java.lang.reflect.Field field = request.getClass().getDeclaredField("headers");
            field.setAccessible(true);
            Map copy = new LinkedHashMap(existing);
            field.set(request, copy);
            return copy;
        } catch (Throwable ignored) { return null; }
    }
    public static Integer status(Object response) {
        try { String value = value(response, "status"); return value == null ? null : Integer.valueOf(value); }
        catch (RuntimeException ignored) { return null; }
    }
    public static String value(Object target, String method) {
        Object value = invoke(target, method);
        return value == null ? null : String.valueOf(value);
    }
    private static Object invoke(Object target, String method) {
        try { return target == null ? null : target.getClass().getMethod(method).invoke(target); }
        catch (Throwable ignored) { return null; }
    }

    public static final class ExecutionState {
        public final HttpClientEvidenceRuntime.ClientState clientState;
        public ExecutionState(HttpClientEvidenceRuntime.ClientState clientState) { this.clientState = clientState; }
    }
}
