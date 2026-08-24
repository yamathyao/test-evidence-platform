package com.talkanything.testevidence.agent.http;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServletDispatchAdviceTest {
    @Test
    void exposesStateMembersForByteBuddyInlineAccessFromDispatcherServlet() throws Exception {
        Constructor<?> constructor = ServletDispatchAdvice.State.class.getDeclaredConstructors()[0];
        Field context = ServletDispatchAdvice.State.class.getDeclaredField("context");
        Field method = ServletDispatchAdvice.State.class.getDeclaredField("method");
        Field target = ServletDispatchAdvice.State.class.getDeclaredField("target");
        Field startedAt = ServletDispatchAdvice.State.class.getDeclaredField("startedAt");
        Method header = ServletDispatchAdvice.class.getDeclaredMethod("header", Object.class, String.class);
        Method value = ServletDispatchAdvice.class.getDeclaredMethod("value", Object.class, String.class);

        assertTrue(Modifier.isPublic(constructor.getModifiers()));
        assertTrue(Modifier.isPublic(context.getModifiers()));
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isPublic(target.getModifiers()));
        assertTrue(Modifier.isPublic(startedAt.getModifiers()));
        assertTrue(Modifier.isPublic(header.getModifiers()));
        assertTrue(Modifier.isPublic(value.getModifiers()));
    }
}
