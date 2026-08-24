package com.talkanything.testevidence.agent.jdbc;

public final class JdbcExecutionScope {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<Integer>();

    private JdbcExecutionScope() { }

    public static boolean enter() {
        Integer value = DEPTH.get();
        int depth = value == null ? 0 : value.intValue();
        DEPTH.set(Integer.valueOf(depth + 1));
        return depth == 0;
    }

    public static void exit() {
        Integer value = DEPTH.get();
        if (value == null || value.intValue() <= 1) {
            DEPTH.remove();
            return;
        }
        DEPTH.set(Integer.valueOf(value.intValue() - 1));
    }
}
