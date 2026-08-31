package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public final class OkHttpNewCallAdvice {
    private static final Map<Object, Object> REQUESTS = Collections.synchronizedMap(new WeakHashMap<Object, Object>());

    private OkHttpNewCallAdvice() { }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Argument(value = 0, readOnly = false, typing = Assigner.Typing.DYNAMIC) Object request) {
        request = copy(request);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.Argument(0) Object request,
                            @Advice.Return(readOnly = true, typing = Assigner.Typing.DYNAMIC) Object call) {
        remember(call, request);
    }

    public static Object copy(Object request) {
        return HttpClientPropagation.copyWithHeaders(request,
                HttpClientPropagation.headers(TestContextHolder.currentOrNull()));
    }

    public static void remember(Object call, Object request) {
        if (call != null && request != null) REQUESTS.put(call, request);
    }

    public static Object requestFor(Object call) { return REQUESTS.get(call); }
}
