package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.blackbox.BlackboxRequestState;
import com.talkanything.testevidence.agent.context.TestContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public final class RequestBodyCorrelationAdvice {
    private RequestBodyCorrelationAdvice() { }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit(@Advice.Return(typing = Assigner.Typing.DYNAMIC) Object body) {
        TestContext context = BlackboxRequestState.matchBody(body);
        HttpEvidenceRuntime.activatePayload(context);
    }
}
