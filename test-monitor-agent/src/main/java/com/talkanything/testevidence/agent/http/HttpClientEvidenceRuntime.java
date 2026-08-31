package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.time.Instant;
import java.util.Optional;

public final class HttpClientEvidenceRuntime {
    private static volatile AsyncEvidenceReporter reporter;
    private static volatile String serviceName;

    private HttpClientEvidenceRuntime() { }

    public static void initialize(String service, AsyncEvidenceReporter value) {
        serviceName = service;
        reporter = value;
    }

    public static ClientState begin(String method, String target) {
        Optional<TestContext> current = TestContextHolder.current();
        if (!current.isPresent()) return null;
        return new ClientState(TestContextHolder.child(), method, target, System.currentTimeMillis());
    }

    public static void complete(ClientState state, Integer status, Throwable failure) {
        if (state == null) return;
        try {
            AsyncEvidenceReporter value = reporter;
            if (value != null) {
                value.report(new HttpEvidence(state.context, serviceName, "CLIENT", state.method, state.target, status,
                        Instant.ofEpochMilli(state.startedAt), Math.max(0, System.currentTimeMillis() - state.startedAt),
                        summary(failure)));
            }
        } catch (Throwable ignored) { }
    }

    private static String summary(Throwable failure) {
        return failure == null ? "" : failure.getClass().getSimpleName();
    }

    public static final class ClientState {
        private final TestContext context;
        private final String method;
        private final String target;
        private final long startedAt;

        private ClientState(TestContext context, String method, String target, long startedAt) {
            this.context = context;
            this.method = method;
            this.target = target;
            this.startedAt = startedAt;
        }
    }
}
