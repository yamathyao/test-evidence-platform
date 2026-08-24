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
        TestContext parent = TestContextHolder.currentOrNull();
        if (parent == null) return null;
        TestContext context = TestContextHolder.child();
        return DubboContextPropagation.inject(attachments, context)
                ? new ConsumerState(context, target, System.currentTimeMillis()) : null;
    }

    public static ProviderState enterProvider(Map<String, String> attachments, String target) {
        DubboContextPropagation.Metadata metadata = DubboContextPropagation.read(attachments);
        if (metadata == null) return null;
        TestContext previous = TestContextHolder.currentOrNull();
        TestContext context = TestContextHolder.enter(metadata.runId(), metadata.caseId(), metadata.profileId(),
                metadata.profileVersion(), metadata.traceId(), metadata.parentSpanId());
        return new ProviderState(previous, context, target, System.currentTimeMillis());
    }

    public static void completeConsumer(ConsumerState state, Throwable failure) {
        if (state != null) report(state.context, "CLIENT", state.target, state.startedAt, failure);
    }

    public static void completeProvider(ProviderState state, Throwable failure) {
        if (state == null) return;
        try { report(state.context, "SERVER", state.target, state.startedAt, failure); }
        finally { TestContextHolder.restore(state.previous); }
    }

    private static void report(TestContext context, String direction, String target, long startedAt, Throwable failure) {
        try {
            AsyncEvidenceReporter value = reporter;
            if (value != null) value.report(new DubboEvidence(context, serviceName, direction, target,
                    Instant.ofEpochMilli(startedAt), Math.max(0, System.currentTimeMillis() - startedAt), summary(failure)));
        } catch (Throwable ignored) { }
    }

    private static String summary(Throwable failure) { return failure == null ? "" : failure.getClass().getSimpleName(); }

    public static final class ConsumerState {
        private final TestContext context;
        private final String target;
        private final long startedAt;

        public ConsumerState(TestContext context, String target, long startedAt) {
            this.context = context;
            this.target = target;
            this.startedAt = startedAt;
        }

        public TestContext context() { return context; }
    }

    public static final class ProviderState {
        private final TestContext previous;
        private final TestContext context;
        private final String target;
        private final long startedAt;

        public ProviderState(TestContext previous, TestContext context, String target, long startedAt) {
            this.previous = previous;
            this.context = context;
            this.target = target;
            this.startedAt = startedAt;
        }

        public TestContext context() { return context; }
    }
}
