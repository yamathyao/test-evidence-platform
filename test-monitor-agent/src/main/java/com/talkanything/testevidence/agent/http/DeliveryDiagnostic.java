package com.talkanything.testevidence.agent.http;

public final class DeliveryDiagnostic {
    private final String runId;
    private final String profileId;
    private final int profileVersion;
    private final String serviceName;
    private final long droppedEvidenceCount;
    private final long deliveryFailureCount;
    private final String lastFailure;

    public DeliveryDiagnostic(String runId, String profileId, int profileVersion, String serviceName,
                              long droppedEvidenceCount, long deliveryFailureCount, String lastFailure) {
        this.runId = runId; this.profileId = profileId; this.profileVersion = profileVersion;
        this.serviceName = serviceName; this.droppedEvidenceCount = droppedEvidenceCount;
        this.deliveryFailureCount = deliveryFailureCount; this.lastFailure = lastFailure;
    }

    public String runId() { return runId; }
    public String profileId() { return profileId; }
    public int profileVersion() { return profileVersion; }
    public String serviceName() { return serviceName; }
    public long droppedEvidenceCount() { return droppedEvidenceCount; }
    public long deliveryFailureCount() { return deliveryFailureCount; }
    public String lastFailure() { return lastFailure; }

    String toJson() {
        return "{\"testRunId\":\"" + quote(runId) + "\",\"profileId\":\"" + quote(profileId)
                + "\",\"profileVersion\":" + profileVersion + ",\"serviceName\":\"" + quote(serviceName)
                + "\",\"droppedEvidenceCount\":" + droppedEvidenceCount + ",\"deliveryFailureCount\":"
                + deliveryFailureCount + ",\"lastFailure\":\"" + quote(lastFailure) + "\"}";
    }

    private static String quote(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
