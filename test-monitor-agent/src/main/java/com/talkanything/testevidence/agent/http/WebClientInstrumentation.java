package com.talkanything.testevidence.agent.http;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class WebClientInstrumentation {
    private WebClientInstrumentation() { }

    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default().with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION).disableClassFormatChanges()
                .type(named("org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction"))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(WebClientMonoAdvice.class).on(named("exchange").and(takesArguments(1)))))
                .installOn(instrumentation);
    }
}