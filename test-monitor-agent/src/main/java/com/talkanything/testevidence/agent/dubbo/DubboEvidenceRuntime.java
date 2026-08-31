package com.talkanything.testevidence.agent.dubbo;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.http.AsyncEvidenceReporter;
import java.time.Instant;
import java.util.Map;

public final class DubboEvidenceRuntime {
    private static volatile AsyncEvidenceReporter reporter;
    private static volatile String serviceName;

    private DubboEvidenceRuntime() { }

    public static void initialize(String service, AsyncEvidenceReporter value) {
        serviceName = service;
        reporter = value;
    }

    public static ConsumerState enterConsumer(Map<String, String> attachments, String target) {
        return enterConsumer(attachments, target, new Object[0]);
    }

    public static ConsumerState enterConsumer(Map<String, String> attachments, String target, Object[] arguments) {
        TestContext parent = TestContextHolder.currentOrNull();
        if (parent == null) return null;
        TestContext context = TestContextHolder.child();
        return DubboContextPropagation.inject(attachments, context)
                ? new ConsumerState(context, target, arguments, System.currentTimeMillis()) : null;
    }

    public static ProviderState enterProvider(Map<String, String> attachments, String target) {
        return enterProvider(attachments, target, new Object[0]);
    }

    public static ProviderState enterProvider(Map<String, String> attachments, String target, Object[] arguments) {
        DubboContextPropagation.Metadata metadata = DubboContextPropagation.read(attachments);
        if (metadata == null) return null;
        TestContext previous = TestContextHolder.currentOrNull();
        TestContext context = TestContextHolder.enter(metadata.runId(), metadata.caseId(), metadata.profileId(),
                metadata.profileVersion(), metadata.traceId(), metadata.parentSpanId(), metadata.payloadCaptureEnabled());
        return new ProviderState(previous, context, target, arguments, System.currentTimeMillis());
    }

    public static void completeConsumer(ConsumerState state, Throwable failure) {
        completeConsumer(state, null, failure);
    }

    public static void completeConsumer(ConsumerState state, Object result, Throwable failure) {
        if (state != null) report(state.context, "CLIENT", state.target, state.arguments, result, state.startedAt, failure);
    }

    public static void completeProvider(ProviderState state, Throwable failure) {
        completeProvider(state, null, failure);
    }

    public static void completeProvider(ProviderState state, Object result, Throwable failure) {
        if (state == null) return;
        try { report(state.context, "SERVER", state.target, state.arguments, result, state.startedAt, failure); }
        finally { TestContextHolder.restore(state.previous); }
    }

    private static void report(TestContext context, String direction, String target, Object[] arguments, Object result,
                               long startedAt, Throwable failure) {
        try {
            AsyncEvidenceReporter value = reporter;
            if (value != null) value.report(new DubboEvidence(context, serviceName, direction, target,
                    Instant.ofEpochMilli(startedAt), Math.max(0, System.currentTimeMillis() - startedAt), summary(failure),
                    payload(context, arguments, result, failure)));
        } catch (Throwable ignored) { }
    }

    private static String summary(Throwable failure) { return failure == null ? "" : failure.getClass().getSimpleName(); }

    private static DubboEvidence.Payload payload(TestContext context, Object[] arguments, Object result, Throwable failure) {
        if (!context.payloadCaptureEnabled()) return null;
        DubboPayloadCapture.Captured request = DubboPayloadCapture.arguments(arguments);
        DubboPayloadCapture.Captured response = failure == null ? DubboPayloadCapture.resultValue(result)
                : DubboPayloadCapture.unavailable();
        return new DubboEvidence.Payload(request, response);
    }

    public static final class ConsumerState {
        private final TestContext context;
        private final String target;
        private final Object[] arguments;
        private final long startedAt;

        public ConsumerState(TestContext context, String target, Object[] arguments, long startedAt) {
            this.context = context;
            this.target = target;
            this.arguments = arguments;
            this.startedAt = startedAt;
        }

        public TestContext context() { return context; }
    }

    public static final class ProviderState {
        private final TestContext previous;
        private final TestContext context;
        private final String target;
        private final Object[] arguments;
        private final long startedAt;

        public ProviderState(TestContext previous, TestContext context, String target, Object[] arguments, long startedAt) {
            this.previous = previous;
            this.context = context;
            this.target = target;
            this.arguments = arguments;
            this.startedAt = startedAt;
        }

        public TestContext context() { return context; }
    }
}
