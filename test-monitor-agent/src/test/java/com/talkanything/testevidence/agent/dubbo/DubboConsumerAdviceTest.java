package com.talkanything.testevidence.agent.dubbo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DubboConsumerAdviceTest {
    @Test
    void resolvesTargetThroughPublicInterfaceWhenInvokerImplementationIsNotPublic() {
        assertEquals(EchoService.class.getName() + "#echo", DubboConsumerAdvice.target(new OpaqueInvoker(), new Invocation()));
    }

    @Test
    void resolvesTargetFromInvocationInvokerWhenAdviceInvokerHasNoInterface() {
        assertEquals(EchoService.class.getName() + "#echo",
                DubboConsumerAdvice.target(null, new InvocationWithInvoker()));
    }

    @Test
    void resolvesTargetFromDubboPathAttachmentWhenProviderInvokerIsUnavailable() {
        assertEquals(EchoService.class.getName() + "#echo",
                DubboConsumerAdvice.target(null, new InvocationWithPath()));
    }

    public interface InvokerView {
        Class<?> getInterface();
    }

    public interface EchoService { }

    private static final class OpaqueInvoker implements InvokerView {
        @Override public Class<?> getInterface() { return EchoService.class; }
    }

    private static final class Invocation {
        public String getMethodName() { return "echo"; }
    }

    private static final class InvocationWithInvoker {
        public String getMethodName() { return "echo"; }
        public InvokerView getInvoker() { return new OpaqueInvoker(); }
    }

    private static final class InvocationWithPath {
        public String getMethodName() { return "echo"; }
        public java.util.Map<String, String> getAttachments() {
            return java.util.Collections.singletonMap("path", EchoService.class.getName());
        }
    }
}
