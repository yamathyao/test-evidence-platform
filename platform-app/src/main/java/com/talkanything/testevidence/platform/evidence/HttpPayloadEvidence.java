package com.talkanything.testevidence.platform.evidence;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "http_payload_evidence")
public class HttpPayloadEvidence {
    @Id private UUID id;
    @OneToOne @JoinColumn(name = "evidence_event_id", nullable = false, unique = true) private EvidenceEvent evidenceEvent;
    @Column(name = "request_content_type") private String requestContentType;
    @Column(name = "response_content_type") private String responseContentType;
    @Column(name = "request_status", nullable = false) private String requestStatus;
    @Column(name = "response_status", nullable = false) private String responseStatus;
    @Column(name = "request_body", columnDefinition = "text") private String requestBody;
    @Column(name = "response_body", columnDefinition = "text") private String responseBody;
    @Column(name = "request_truncated", nullable = false) private boolean requestTruncated;
    @Column(name = "response_truncated", nullable = false) private boolean responseTruncated;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected HttpPayloadEvidence() { }
    HttpPayloadEvidence(EvidenceEvent event, EvidenceController.HttpPayloadRequest payload, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.evidenceEvent = event; this.requestContentType = payload.requestContentType();
        this.responseContentType = payload.responseContentType(); this.requestStatus = payload.requestStatus();
        this.responseStatus = payload.responseStatus(); this.requestBody = payload.requestBody(); this.responseBody = payload.responseBody();
        this.requestTruncated = payload.requestTruncated(); this.responseTruncated = payload.responseTruncated();
        this.expiresAt = expiresAt; this.createdAt = Instant.now();
    }
    public String getRequestContentType() { return requestContentType; }
    public String getResponseContentType() { return responseContentType; }
    public String getRequestStatus() { return requestStatus; }
    public String getResponseStatus() { return responseStatus; }
    public String getRequestBody() { return requestBody; }
    public String getResponseBody() { return responseBody; }
    public boolean isRequestTruncated() { return requestTruncated; }
    public boolean isResponseTruncated() { return responseTruncated; }
    public Instant getExpiresAt() { return expiresAt; }
    public void shortenExpiry(Instant value) { this.expiresAt = value; }
}
