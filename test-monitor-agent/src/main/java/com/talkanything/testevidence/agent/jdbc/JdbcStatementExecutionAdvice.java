package com.talkanything.testevidence.agent.jdbc;

import net.bytebuddy.asm.Advice;

public final class JdbcStatementExecutionAdvice {
    private JdbcStatementExecutionAdvice() { }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ExecutionState enter(@Advice.This Object statement, @Advice.AllArguments Object[] arguments,
                                       @Advice.Origin("#m") String method) {
        String sqlTemplate = JdbcEvidence.maskStatementSql(sql(arguments, statement));
        return new ExecutionState(statement, sqlTemplate, operation(sqlTemplate, method), System.currentTimeMillis(),
                JdbcExecutionScope.enter());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ExecutionState state, @Advice.Thrown Throwable failure) {
        if (state == null) return;
        try {
            if (state.root()) {
                JdbcEvidenceRuntime.complete(state.statement, state.sqlTemplate, state.operation, state.startedAt, failure);
            }
        } finally {
            JdbcExecutionScope.exit();
        }
    }

    public static String sql(Object[] arguments, Object statement) {
        if (arguments != null) {
            for (Object argument : arguments) {
                if (argument instanceof String) return (String) argument;
            }
        }
        return MySqlStatementExecutionAdvice.originalSql(statement);
    }

    public static String operation(String sqlTemplate, String method) {
        return JdbcSqlClassifier.operation(sqlTemplate, fallbackOperation(method));
    }

    private static String fallbackOperation(String method) {
        if ("executeQuery".equals(method)) return "SELECT";
        if ("executeUpdate".equals(method) || "executeLargeUpdate".equals(method)
                || "executeBatch".equals(method)) return "UPDATE";
        return "EXECUTE";
    }

    public static final class ExecutionState {
        public final Object statement;
        public final String sqlTemplate;
        public final String operation;
        public final long startedAt;
        private final boolean root;

        public ExecutionState(Object statement, String sqlTemplate, String operation, long startedAt, boolean root) {
            this.statement = statement;
            this.sqlTemplate = sqlTemplate;
            this.operation = operation;
            this.startedAt = startedAt;
            this.root = root;
        }

        public String operation() {
            return operation;
        }

        public boolean root() {
            return root;
        }
    }
}
