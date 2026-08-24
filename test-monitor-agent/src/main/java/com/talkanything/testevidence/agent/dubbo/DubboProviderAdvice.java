package com.talkanything.testevidence.agent.dubbo;

import net.bytebuddy.asm.Advice;

public final class DubboProviderAdvice {
    private DubboProviderAdvice() { }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static DubboEvidenceRuntime.ProviderState enter(@Advice.Argument(0) Object invoker,
                                                            @Advice.Argument(1) Object invocation) {
        return DubboEvidenceRuntime.enterProvider(DubboConsumerAdvice.attachments(invocation),
                DubboConsumerAdvice.target(invoker, invocation));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter DubboEvidenceRuntime.ProviderState state, @Advice.Thrown Throwable failure) {
        DubboEvidenceRuntime.completeProvider(state, failure);
    }
}
