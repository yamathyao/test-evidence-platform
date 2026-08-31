package com.talkanything.testevidence.agent.http;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class OkHttpInstrumentation {
    private OkHttpInstrumentation() { }

    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .disableClassFormatChanges()
                .type(named("okhttp3.OkHttpClient"))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(OkHttpNewCallAdvice.class).on(named("newCall").and(takesArguments(1)))))
                .type(named("okhttp3.RealCall"))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(OkHttpExecutionAdvice.class).on(named("execute").and(takesArguments(0)))))
                .installOn(instrumentation);
    }
}