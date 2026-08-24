package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.UUID;

public interface HttpTriggerClient {
    HttpTriggerResponse execute(JsonNode triggerConfig, TriggerContext context) throws IOException, InterruptedException;
    record TriggerContext(UUID runId, UUID caseId, UUID profileId, int profileVersion, int timeoutSeconds,
                          boolean payloadCaptureEnabled) {
        TriggerContext(UUID runId, UUID caseId, UUID profileId, int profileVersion, int timeoutSeconds) {
            this(runId, caseId, profileId, profileVersion, timeoutSeconds, false);
        }
    }
}
