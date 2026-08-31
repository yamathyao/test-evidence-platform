package com.talkanything.testevidence.agent.dubbo;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DubboContextPropagationTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void writesCompleteMetadataWithoutOverwritingConflictingAttachment() {
        TestContext context = TestContextHolder.enter("run", "case", "profile", 1, true);
        Map<String, String> attachments = new HashMap<String, String>();

        assertTrue(DubboContextPropagation.inject(attachments, context));
        assertEquals("run", attachments.get("x-test-run-id"));
        assertEquals("case", attachments.get("x-test-case-id"));
        assertEquals("profile", attachments.get("x-test-profile-id"));
        assertEquals("1", attachments.get("x-test-profile-version"));
        assertEquals(context.traceId(), attachments.get("x-test-trace-id"));
        assertEquals(context.spanId(), attachments.get("x-test-parent-span-id"));
        assertEquals("true", attachments.get("x-test-capture-protocol-payload"));

        attachments.put("x-test-run-id", "business-run");

        assertFalse(DubboContextPropagation.inject(attachments, context));
        assertEquals("business-run", attachments.get("x-test-run-id"));
    }
}
