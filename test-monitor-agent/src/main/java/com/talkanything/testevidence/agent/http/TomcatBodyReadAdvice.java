package com.talkanything.testevidence.agent.http;

import java.nio.ByteBuffer;
import net.bytebuddy.asm.Advice;

public final class TomcatBodyReadAdvice {
    private TomcatBodyReadAdvice() { }
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void after(@Advice.AllArguments Object[] arguments, @Advice.Return int count) {
        if (count <= 0 || arguments.length == 0) return;
        Object value = arguments[0];
        if (value instanceof byte[]) {
            int offset = arguments.length > 1 && arguments[1] instanceof Integer ? ((Integer) arguments[1]).intValue() : 0;
            HttpEvidenceRuntime.requestBytes((byte[]) value, offset, count);
        } else if (value instanceof ByteBuffer) {
            byte[] bytes = bytesAfterRead((ByteBuffer) value, count);
            if (bytes.length > 0) HttpEvidenceRuntime.requestBytes(bytes, 0, bytes.length);
        }
    }

    static byte[] bytesAfterRead(ByteBuffer buffer, int count) {
        if (buffer == null || count <= 0 || buffer.position() < count) return new byte[0];
        ByteBuffer copy = buffer.duplicate();
        int end = copy.position();
        copy.position(end - count);
        copy.limit(end);
        byte[] bytes = new byte[count];
        copy.get(bytes);
        return bytes;
    }
}
