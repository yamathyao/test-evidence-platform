package com.talkanything.testevidence.agent.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.asm.Advice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static net.bytebuddy.matcher.ElementMatchers.named;

class TomcatByteBufferAdviceTest {
    @Test
    void extractsBytesReadIntoAnAdvancedBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.put("{\"id\":1}".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals("{\"id\":1}".getBytes(StandardCharsets.UTF_8),
                TomcatBodyReadAdvice.bytesAfterRead(buffer, 8));
    }

    @Test
    void copiesRemainingBytesBeforeBufferIsWritten() {
        ByteBuffer buffer = ByteBuffer.wrap("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals("{\"ok\":true}".getBytes(StandardCharsets.UTF_8),
                TomcatBodyWriteAdvice.bytesBeforeWrite(buffer));
    }

    @Test
    void appliesReadAdviceToByteArrayMethod() {
        assertDoesNotThrow(() -> new ByteBuddy()
                .subclass(Object.class)
                .defineMethod("read", int.class, Visibility.PUBLIC)
                .withParameters(byte[].class)
                .intercept(FixedValue.value(0))
                .visit(Advice.to(TomcatBodyReadAdvice.class).on(named("read")))
                .make());
    }

    @Test
    void appliesWriteAdviceToByteArrayMethod() {
        assertDoesNotThrow(() -> new ByteBuddy()
                .subclass(Object.class)
                .defineMethod("write", void.class, Visibility.PUBLIC)
                .withParameters(byte[].class)
                .intercept(StubMethod.INSTANCE)
                .visit(Advice.to(TomcatBodyWriteAdvice.class).on(named("write")))
                .make());
    }
}
