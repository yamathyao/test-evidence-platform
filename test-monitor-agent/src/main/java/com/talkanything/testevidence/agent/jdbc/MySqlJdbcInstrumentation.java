package com.talkanything.testevidence.agent.jdbc;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class MySqlJdbcInstrumentation {
    private MySqlJdbcInstrumentation() { }

    public static void install(Instrumentation instrumentation) {
        install(instrumentation, AgentBuilder.Listener.NoOp.INSTANCE);
    }

    static void install(Instrumentation instrumentation, AgentBuilder.Listener listener) {
        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(listener)
                .disableClassFormatChanges()
                .type(named("com.mysql.jdbc.PreparedStatement")
                        .or(named("com.mysql.cj.jdbc.ClientPreparedStatement"))
                        .or(named("com.zaxxer.hikari.pool.HikariProxyPreparedStatement"))
                        .or(named("com.alibaba.druid.pool.DruidPooledPreparedStatement"))
                        .or(named("com.alibaba.druid.pool.DruidPooledCallableStatement")))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(MySqlPreparedStatementAdvice.class)
                                .on(nameStartsWith("set").and(takesArguments(2)).and(takesArgument(0, int.class))))
                        .visit(Advice.to(MySqlStatementExecutionAdvice.class)
                                .on(named("execute").or(named("executeQuery")).or(named("executeUpdate"))
                                        .or(named("executeBatch")).or(named("executeLargeUpdate")))))
                .installOn(instrumentation);
    }
}
