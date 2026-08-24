package com.talkanything.testevidence.agent.jdbc;

import net.bytebuddy.asm.Advice;

public final class MySqlStatementExecutionAdvice {
    private MySqlStatementExecutionAdvice() { }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ExecutionState enter(@Advice.This Object statement, @Advice.Origin("#m") String method) {
        String sqlTemplate = originalSql(statement);
        return new ExecutionState(statement, sqlTemplate, operation(sqlTemplate, method), System.currentTimeMillis(),
                JdbcExecutionScope.enter());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter ExecutionState state, @Advice.Thrown Throwable failure) {
        try {
            if (state != null && state.root()) {
                JdbcEvidenceRuntime.complete(state.statement, state.sqlTemplate, state.operation, state.startedAt, failure);
            }
        } finally {
            JdbcExecutionScope.exit();
        }
    }

    public static String originalSql(Object statement) {
        Object candidate = statement;
        for (int level = 0; candidate != null && level < 2; level++) {
            String sql = readSql(candidate, "getOriginalSql");
            if (!sql.isEmpty()) return sql;
            sql = readSql(candidate, "getPreparedSql");
            if (!sql.isEmpty()) return sql;
            candidate = unwrapStatement(candidate);
        }
        return "";
    }

    public static String readSql(Object statement, String methodName) {
        try {
            Object sql = statement.getClass().getMethod(methodName).invoke(statement);
            return sql == null ? "" : String.valueOf(sql);
        } catch (Throwable ignored) { }
        return "";
    }

    public static Object unwrapStatement(Object statement) {
        try {
            Object value = statement.getClass().getMethod("unwrap", Class.class)
                    .invoke(statement, java.sql.Statement.class);
            return value == statement ? null : value;
        } catch (Throwable ignored) { return null; }
    }

    public static String operation(String sqlTemplate, String method) {
        if ("executeQuery".equals(method)) return "SELECT";
        if ("executeUpdate".equals(method) || "executeLargeUpdate".equals(method) || "executeBatch".equals(method)) {
            return JdbcSqlClassifier.operation(sqlTemplate, "UPDATE");
        }
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

        public boolean root() {
            return root;
        }
    }
}
