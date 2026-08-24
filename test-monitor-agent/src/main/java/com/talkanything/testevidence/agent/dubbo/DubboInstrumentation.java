package com.talkanything.testevidence.agent.dubbo;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public final class DubboInstrumentation {
    private DubboInstrumentation() { }

    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .disableClassFormatChanges()
                .type(named("com.alibaba.dubbo.rpc.protocol.dubbo.DubboInvoker")
                        .or(named("org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker")))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                        .visit(Advice.to(DubboConsumerAdvice.class).on(named("doInvoke").and(takesArguments(1)))))
                .installOn(instrumentation);

        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .disableClassFormatChanges()
                .type(named("com.alibaba.dubbo.rpc.filter.ContextFilter")
                        .or(named("org.apache.dubbo.rpc.filter.ContextFilter")))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                        .visit(Advice.to(DubboProviderAdvice.class).on(named("invoke").and(takesArguments(2)))))
                .installOn(instrumentation);
    }
}
