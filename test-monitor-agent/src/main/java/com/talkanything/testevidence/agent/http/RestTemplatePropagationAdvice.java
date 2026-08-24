package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class RestTemplatePropagationAdvice {
    private RestTemplatePropagationAdvice() { }

    public static Object wrap(Object callback) {
        Optional<TestContext> current = TestContextHolder.current();
        if (callback == null || !current.isPresent()) return callback;
        Set<Class<?>> interfaces = interfacesOf(callback.getClass());
        if (interfaces.isEmpty()) return callback;
        ClassLoader loader = callback.getClass().getClassLoader();
        if (loader == null) loader = RestTemplatePropagationAdvice.class.getClassLoader();
        return Proxy.newProxyInstance(loader, interfaces.toArray(new Class<?>[interfaces.size()]),
                new PropagatingCallback(callback, current.get()));
    }

    private static Set<Class<?>> interfacesOf(Class<?> type) {
        Set<Class<?>> interfaces = new LinkedHashSet<Class<?>>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            addInterfaces(current.getInterfaces(), interfaces);
        }
        return interfaces;
    }

    private static void addInterfaces(Class<?>[] candidates, Set<Class<?>> interfaces) {
        for (Class<?> candidate : candidates) {
            if (interfaces.add(candidate)) addInterfaces(candidate.getInterfaces(), interfaces);
        }
    }

    private static final class PropagatingCallback implements InvocationHandler {
        private final Object delegate;
        private final TestContext context;
        private PropagatingCallback(Object delegate, TestContext context) { this.delegate = delegate; this.context = context; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if ("doWithRequest".equals(method.getName()) && arguments != null && arguments.length == 1) inject(arguments[0]);
            try {
                return method.invoke(delegate, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }

        private void inject(Object request) {
            try {
                Object headers = request.getClass().getMethod("getHeaders").invoke(request);
                put(headers, "X-Test-Run-Id", context.runId());
                put(headers, "X-Test-Case-Id", context.caseId());
                put(headers, "X-Test-Profile-Id", context.profileId());
                put(headers, "X-Test-Profile-Version", Integer.toString(context.profileVersion()));
                put(headers, "X-Test-Trace-Id", context.traceId());
                put(headers, "X-Test-Parent-Span-Id", context.spanId());
                if (context.payloadCaptureEnabled()) put(headers, "X-Test-Capture-Http-Payload", "true");
                AgentDiagnostics.log("http.payload.propagated enabled=" + context.payloadCaptureEnabled());
            } catch (Throwable ignored) { }
        }

        private void put(Object headers, String name, String value) {
            try {
                Object existing = headers.getClass().getMethod("getFirst", String.class).invoke(headers, name);
                if (existing == null) headers.getClass().getMethod("set", String.class, String.class).invoke(headers, name, value);
            } catch (Throwable ignored) { }
        }
    }
}
