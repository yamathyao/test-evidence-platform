package com.talkanything.testevidence.agent.evidence;

public interface EvidencePayload {
    String toJson();
    String runId();
    String profileId();
    int profileVersion();
    String serviceName();
}
