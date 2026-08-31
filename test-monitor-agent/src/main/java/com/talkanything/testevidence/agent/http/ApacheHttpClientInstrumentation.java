package com.talkanything.testevidence.agent.http;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public final class ApacheHttpClientInstrumentation {
    private ApacheHttpClientInstrumentation() { }

    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .disableClassFormatChanges()
                .type(hasSuperType(named("org.apache.http.impl.client.CloseableHttpClient"))
                        .or(hasSuperType(named("org.apache.hc.client5.http.impl.classic.CloseableHttpClient"))))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(HttpClientExecutionAdvice.class).on(named("doExecute"))))
                .installOn(instrumentation);
    }
}
