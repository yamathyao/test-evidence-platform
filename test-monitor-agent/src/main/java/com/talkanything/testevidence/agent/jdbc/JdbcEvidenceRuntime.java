package com.talkanything.testevidence.agent.jdbc;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.http.AsyncEvidenceReporter;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class JdbcEvidenceRuntime {
    private static final Map<Object, JdbcStatementState> STATES = Collections.synchronizedMap(new WeakHashMap<Object, JdbcStatementState>());
    private static volatile AsyncEvidenceReporter reporter;
    private static volatile String serviceName;
    private static volatile JdbcSqlClassifier.Filter sqlFilter = JdbcSqlClassifier.filter(
            Collections.<String>emptyList(), Collections.<String>emptyList());

    private JdbcEvidenceRuntime() { }

    public static void initialize(String service, AsyncEvidenceReporter value) {
        initialize(service, value, Collections.<String>emptyList(), Collections.<String>emptyList());
    }

    public static void initialize(String service, AsyncEvidenceReporter value, List<String> prefixes, List<String> regexes) {
        serviceName = service;
        reporter = value;
        sqlFilter = JdbcSqlClassifier.filter(prefixes, regexes);
    }

    public static void recordParameter(Object statement, String setter, int index, Object value) {
        if (statement == null || !TestContextHolder.current().isPresent()) return;
        state(statement).record(setter, index, value);
    }

    public static void complete(Object statement, String sqlTemplate, String operation, long startedAt, Throwable failure) {
        if (!TestContextHolder.current().isPresent()) return;
        if (sqlFilter.shouldIgnore(sqlTemplate)) return;
        try {
            AsyncEvidenceReporter value = reporter;
            if (value == null) return;
            TestContext context = TestContextHolder.child();
            value.report(new JdbcEvidence(context, serviceName, operation, sqlTemplate, parameters(statement),
                    Instant.ofEpochMilli(startedAt), Math.max(0, System.currentTimeMillis() - startedAt), summary(failure)));
        } catch (Throwable ignored) { }
    }

    private static JdbcStatementState state(Object statement) {
        synchronized (STATES) {
            JdbcStatementState value = STATES.get(statement);
            if (value == null) {
                value = new JdbcStatementState();
                STATES.put(statement, value);
            }
            return value;
        }
    }

    private static java.util.List<JdbcValueSummary> parameters(Object statement) {
        if (statement == null) return Collections.emptyList();
        synchronized (STATES) {
            JdbcStatementState value = STATES.get(statement);
            return value == null ? Collections.<JdbcValueSummary>emptyList() : value.parameters();
        }
    }

    private static String summary(Throwable failure) { return failure == null ? "" : failure.getClass().getSimpleName(); }
}
