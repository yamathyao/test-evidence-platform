package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public final class HttpClientExecutionAdvice {
    private HttpClientExecutionAdvice() { }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ExecutionState enter(@Advice.AllArguments Object[] arguments) {
        Object request = request(arguments);
        HttpClientPropagation.inject(request, TestContextHolder.currentOrNull());
        return new ExecutionState(HttpClientEvidenceRuntime.begin(method(request), target(request)));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ExecutionState state,
                            @Advice.Return(readOnly = true, typing = Assigner.Typing.DYNAMIC) Object response,
                            @Advice.Thrown Throwable failure) {
        HttpClientEvidenceRuntime.complete(state == null ? null : state.clientState, status(response), failure);
    }

    public static Object request(Object[] arguments) {
        if (arguments == null) return null;
        for (Object argument : arguments) if (argument != null && method(argument) != null) return argument;
        return null;
    }

    public static String method(Object request) {
        String value = string(request, "getMethod");
        if (value == null) value = string(request, "method");
        return value == null ? string(invoke(request, "getRequestLine"), "getMethod") : value;
    }

    public static String target(Object request) {
        String value = string(request, "getURI");
        if (value == null) value = string(request, "getRequestUri");
        if (value == null) value = string(request, "getUri");
        if (value == null) value = string(request, "url");
        return value == null ? string(invoke(request, "getRequestLine"), "getUri") : value;
    }

    public static Integer status(Object response) {
        String value = string(response, "getCode");
        if (value == null) value = string(invoke(response, "getStatusLine"), "getStatusCode");
        try { return value == null ? null : Integer.valueOf(value); }
        catch (RuntimeException ignored) { return null; }
    }

    private static String string(Object target, String method) {
        Object value = invoke(target, method);
        return value == null ? null : String.valueOf(value);
    }

    private static Object invoke(Object target, String method) {
        if (target == null) return null;
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (Throwable ignored) { return null; }
    }

    public static final class ExecutionState {
        public final HttpClientEvidenceRuntime.ClientState clientState;
        public ExecutionState(HttpClientEvidenceRuntime.ClientState clientState) { this.clientState = clientState; }
    }
}
