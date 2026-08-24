package com.talkanything.testevidence.agent.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestContextHolderTest {
    @Test
    void nestedSpanInheritsTraceAndIsClearedWithScope() {
        TestContext root = TestContextHolder.enter("run-1", "case-1", "profile-1", 1);
        TestContext child = TestContextHolder.child();

        assertEquals(root.traceId(), child.traceId());
        assertEquals(root.spanId(), child.parentSpanId());
        assertTrue(TestContextHolder.current().isPresent());

        TestContextHolder.clear();

        assertFalse(TestContextHolder.current().isPresent());
    }

    @Test
    void inboundContextUsesProvidedTraceAndParentSpan() {
        TestContext context = TestContextHolder.enter("run-1", "case-1", "profile-1", 1, "trace-upstream", "span-upstream");

        assertEquals("trace-upstream", context.traceId());
        assertEquals("span-upstream", context.parentSpanId());
        assertFalse("span-upstream".equals(context.spanId()));

        TestContextHolder.clear();
    }
}
