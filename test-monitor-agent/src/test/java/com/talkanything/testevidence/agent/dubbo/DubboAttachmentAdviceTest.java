package com.talkanything.testevidence.agent.dubbo;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DubboAttachmentAdviceTest {
    @Test
    void addsRunIdWhenAttachmentIsAbsent() {
        Map<String, String> attachments = new HashMap<String, String>();

        DubboAttachmentAdvice.inject(attachments, "run-agent-001");

        assertEquals("run-agent-001", attachments.get("x-test-run-id"));
    }

    @Test
    void preservesExistingRunId() {
        Map<String, String> attachments = new HashMap<String, String>();
        attachments.put("x-test-run-id", "run-existing-001");

        DubboAttachmentAdvice.inject(attachments, "run-agent-001");

        assertEquals("run-existing-001", attachments.get("x-test-run-id"));
    }
}
