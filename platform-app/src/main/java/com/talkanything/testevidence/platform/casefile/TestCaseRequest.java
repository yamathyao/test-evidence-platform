package com.talkanything.testevidence.platform.casefile;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record TestCaseRequest(String name, UUID profileId, TriggerType triggerType, JsonNode triggerConfig,
                              int timeoutSeconds, boolean httpPayloadCaptureEnabled, List<AssertionRequest> assertions) {
    public TestCaseRequest(String name, UUID profileId, TriggerType triggerType, JsonNode triggerConfig,
                           int timeoutSeconds, List<AssertionRequest> assertions) {
        this(name, profileId, triggerType, triggerConfig, timeoutSeconds, false, assertions);
    }
    public record AssertionRequest(int sequenceNo, String type, JsonNode definition) { }
}
