package com.talkanything.testevidence.agent.jdbc;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public final class MySqlPreparedStatementAdvice {
    private MySqlPreparedStatementAdvice() { }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void parameter(@Advice.This Object statement, @Advice.Origin("#m") String setter,
                                 @Advice.Argument(0) int index,
                                 @Advice.Argument(value = 1, typing = Assigner.Typing.DYNAMIC) Object value,
                                 @Advice.Thrown Throwable failure) {
        if (failure == null) JdbcEvidenceRuntime.recordParameter(statement, setter, index, value);
    }

}
