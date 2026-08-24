package com.talkanything.testevidence.agent.dubbo;

import java.util.Map;

public final class DubboAttachmentAdvice {
    private static final String TEST_RUN_ID_KEY = "x-test-run-id";

    private DubboAttachmentAdvice() {
    }

    public static void inject(Map<String, String> attachments, String testRunId) {
        if (attachments != null && testRunId != null && !testRunId.isEmpty()
                && !attachments.containsKey(TEST_RUN_ID_KEY)) {
            attachments.put(TEST_RUN_ID_KEY, testRunId);
        }
    }
}
