package com.talkanything.testevidence.agent.http;

import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public final class OkHttpExecutionAdvice {
    private OkHttpExecutionAdvice() { }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ExecutionState enter(@Advice.This Object call) {
        return new ExecutionState(HttpClientExecutionAdvice.enter(new Object[] {request(call)}));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ExecutionState state,
                            @Advice.Return(readOnly = true, typing = Assigner.Typing.DYNAMIC) Object response,
                            @Advice.Thrown Throwable failure) {
        HttpClientExecutionAdvice.exit(state == null ? null : state.delegate, response, failure);
    }

    public static Object request(Object call) {
        try {
            if (call == null) return null;
            Object remembered = OkHttpNewCallAdvice.requestFor(call);
            if (remembered != null) return remembered;
            Method method = call.getClass().getMethod("request");
            method.setAccessible(true);
            return method.invoke(call);
        }
        catch (Throwable ignored) { return null; }
    }

    public static final class ExecutionState {
        public final HttpClientExecutionAdvice.ExecutionState delegate;
        public ExecutionState(HttpClientExecutionAdvice.ExecutionState delegate) { this.delegate = delegate; }
    }
}
