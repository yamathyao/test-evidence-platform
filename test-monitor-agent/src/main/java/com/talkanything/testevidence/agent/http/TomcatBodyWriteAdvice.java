package com.talkanything.testevidence.agent.http;

import java.nio.ByteBuffer;
import net.bytebuddy.asm.Advice;

public final class TomcatBodyWriteAdvice {
    private TomcatBodyWriteAdvice() { }
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static byte[] before(@Advice.AllArguments Object[] arguments) {
        return arguments.length > 0 && arguments[0] instanceof ByteBuffer
                ? bytesBeforeWrite((ByteBuffer) arguments[0]) : null;
    }
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(@Advice.AllArguments Object[] arguments, @Advice.Enter byte[] bufferBytes) {
        if (bufferBytes != null && bufferBytes.length > 0) {
            HttpEvidenceRuntime.responseBytes(bufferBytes, 0, bufferBytes.length);
        } else if (arguments.length > 0 && arguments[0] instanceof byte[]) {
            byte[] bytes = (byte[]) arguments[0];
            int offset = arguments.length > 1 ? ((Integer) arguments[1]).intValue() : 0;
            int length = arguments.length > 2 ? ((Integer) arguments[2]).intValue() : bytes.length;
            HttpEvidenceRuntime.responseBytes(bytes, offset, length);
        } else if (arguments.length > 0 && arguments[0] instanceof Integer) {
            HttpEvidenceRuntime.responseBytes(new byte[] { (byte) ((Integer) arguments[0]).intValue() }, 0, 1);
        }
    }

    static byte[] bytesBeforeWrite(ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) return new byte[0];
        ByteBuffer copy = buffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
