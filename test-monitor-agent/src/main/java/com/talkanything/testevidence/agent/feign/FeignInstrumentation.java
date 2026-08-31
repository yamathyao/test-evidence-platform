package com.talkanything.testevidence.agent.feign;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public final class FeignInstrumentation {
    private FeignInstrumentation() { }

    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default().with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION).disableClassFormatChanges()
                .type(hasSuperType(named("feign.Client")))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(FeignExecutionAdvice.class).on(named("execute"))))
                .installOn(instrumentation);
    }
}