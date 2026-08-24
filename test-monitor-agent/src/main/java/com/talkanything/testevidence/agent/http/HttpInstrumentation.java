package com.talkanything.testevidence.agent.http;

import java.lang.instrument.Instrumentation;
import java.nio.ByteBuffer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;

public final class HttpInstrumentation {
    private HttpInstrumentation() { }
    public static void install(Instrumentation instrumentation) {
        install(instrumentation, AgentBuilder.Listener.NoOp.INSTANCE);
    }

    static void install(Instrumentation instrumentation, AgentBuilder.Listener listener) {
        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(listener)
                .disableClassFormatChanges()
                .type(ElementMatchers.named("org.springframework.web.servlet.DispatcherServlet"))
                .transform((builder, type, loader, module, domain) -> builder.visit(Advice.to(ServletDispatchAdvice.class)
                        .on(ElementMatchers.named("doDispatch").and(ElementMatchers.takesArguments(2)))))
                .type(ElementMatchers.named("org.springframework.web.client.RestTemplate"))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(RestTemplateAdvice.class)
                                .on(ElementMatchers.named("doExecute").and(ElementMatchers.takesArguments(4))))
                        .visit(Advice.to(RestTemplateV6Advice.class)
                                .on(ElementMatchers.named("doExecute").and(ElementMatchers.takesArguments(5)))))
                .type(ElementMatchers.named("org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodArgumentResolver"))
                .transform((builder, type, loader, module, domain) -> builder.visit(Advice.to(RequestBodyCorrelationAdvice.class)
                        .on(ElementMatchers.named("readWithMessageConverters"))))
                .type(ElementMatchers.named("org.apache.catalina.connector.CoyoteInputStream"))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(TomcatBodyReadAdvice.class).on(ElementMatchers.named("read").and(ElementMatchers.takesArguments(byte[].class))))
                        .visit(Advice.to(TomcatBodyReadAdvice.class).on(ElementMatchers.named("read").and(ElementMatchers.takesArguments(byte[].class, int.class, int.class))))
                        .visit(Advice.to(TomcatBodyReadAdvice.class).on(ElementMatchers.named("read").and(ElementMatchers.takesArguments(ByteBuffer.class)))))
                .type(ElementMatchers.named("org.apache.catalina.connector.CoyoteOutputStream"))
                .transform((builder, type, loader, module, domain) -> builder
                        .visit(Advice.to(TomcatBodyWriteAdvice.class).on(ElementMatchers.named("write").and(ElementMatchers.takesArguments(byte[].class))))
                        .visit(Advice.to(TomcatBodyWriteAdvice.class).on(ElementMatchers.named("write").and(ElementMatchers.takesArguments(byte[].class, int.class, int.class))))
                        .visit(Advice.to(TomcatBodyWriteAdvice.class).on(ElementMatchers.named("write").and(ElementMatchers.takesArguments(int.class))))
                        .visit(Advice.to(TomcatBodyWriteAdvice.class).on(ElementMatchers.named("write").and(ElementMatchers.takesArguments(ByteBuffer.class)))))
                .installOn(instrumentation);
    }

    public static class RestTemplateAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void enter(@Advice.Argument(value = 2, readOnly = false, typing = Assigner.Typing.DYNAMIC) Object callback) {
            callback = RestTemplatePropagationAdvice.wrap(callback);
        }
    }

    public static class RestTemplateV6Advice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void enter(@Advice.Argument(value = 3, readOnly = false, typing = Assigner.Typing.DYNAMIC) Object callback) {
            callback = RestTemplatePropagationAdvice.wrap(callback);
        }
    }
}
