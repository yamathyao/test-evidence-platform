package com.talkanything.testevidence.agent.dubbo;

import java.util.Map;
import net.bytebuddy.asm.Advice;

public final class DubboConsumerAdvice {
    private DubboConsumerAdvice() { }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static DubboEvidenceRuntime.ConsumerState enter(@Advice.This Object invoker, @Advice.Argument(0) Object invocation) {
        return DubboEvidenceRuntime.enterConsumer(attachments(invocation), target(invoker, invocation));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter DubboEvidenceRuntime.ConsumerState state, @Advice.Thrown Throwable failure) {
        DubboEvidenceRuntime.completeConsumer(state, failure);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> attachments(Object invocation) {
        try {
            Object value = invocation.getClass().getMethod("getAttachments").invoke(invocation);
            return value instanceof Map ? (Map<String, String>) value : null;
        } catch (Throwable ignored) { return null; }
    }

    public static String target(Object invoker, Object invocation) {
        Object method = invoke(invocation, "getMethodName");
        Object serviceType = invoke(invoker, "getInterface");
        if (serviceType == null) serviceType = invoke(invoke(invocation, "getInvoker"), "getInterface");
        String service = serviceName(serviceType);
        if (service == null) service = serviceName(invoke(invoke(invoker, "getUrl"), "getPath"));
        if (service == null) {
            Map<String, String> values = attachments(invocation);
            service = values == null ? null : serviceName(values.get("path"));
        }
        if (service != null && method != null) return service + "#" + method;
        return "dubbo#unknown";
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) return null;
        try { return target.getClass().getMethod(methodName).invoke(target); }
        catch (Throwable ignored) { return null; }
    }

    private static String serviceName(Object serviceType) {
        if (serviceType instanceof Class) return ((Class<?>) serviceType).getName();
        if (serviceType instanceof String && !((String) serviceType).isEmpty()) return (String) serviceType;
        return null;
    }
}
