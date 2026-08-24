package com.talkanything.testevidence.platform.casefile;

import com.talkanything.testevidence.platform.profile.CaptureProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "test_case")
public class TestCase {
    @Id private UUID id;
    @Column(nullable = false) private String name;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "profile_id", nullable = false)
    private CaptureProfile profile;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_config_json", nullable = false, columnDefinition = "jsonb")
    private String triggerConfigJson;
    @Column(name = "timeout_seconds", nullable = false) private int timeoutSeconds;
    @Column(name = "http_payload_capture_enabled", nullable = false) private boolean httpPayloadCaptureEnabled;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @OneToMany(mappedBy = "testCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo") private List<Assertion> assertions = new ArrayList<Assertion>();

    protected TestCase() { }

    TestCase(String name, CaptureProfile profile, TriggerType triggerType, String triggerConfigJson, int timeoutSeconds,
             boolean httpPayloadCaptureEnabled) {
        this.id = UUID.randomUUID(); this.name = name; this.profile = profile; this.triggerType = triggerType;
        this.triggerConfigJson = triggerConfigJson; this.timeoutSeconds = timeoutSeconds;
        this.httpPayloadCaptureEnabled = httpPayloadCaptureEnabled;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }

    void addAssertion(int sequenceNo, String type, String definitionJson) {
        assertions.add(new Assertion(this, sequenceNo, type, definitionJson));
    }
    void replace(String name, CaptureProfile profile, TriggerType triggerType, String triggerConfigJson,
                 int timeoutSeconds, boolean httpPayloadCaptureEnabled, List<AssertionRequestData> replacements) {
        this.name = name; this.profile = profile; this.triggerType = triggerType;
        this.triggerConfigJson = triggerConfigJson; this.timeoutSeconds = timeoutSeconds;
        this.httpPayloadCaptureEnabled = httpPayloadCaptureEnabled; this.updatedAt = Instant.now();
        assertions.clear();
        for (AssertionRequestData item : replacements) addAssertion(item.sequenceNo(), item.type(), item.definitionJson());
    }
    void complete(Instant now) { this.completedAt = now; this.updatedAt = now; }
    void reopen() { this.completedAt = null; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public CaptureProfile getProfile() { return profile; }
    public TriggerType getTriggerType() { return triggerType; }
    public String getTriggerConfigJson() { return triggerConfigJson; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public boolean isHttpPayloadCaptureEnabled() { return httpPayloadCaptureEnabled; }
    public Instant getCompletedAt() { return completedAt; }
    public List<Assertion> getAssertions() { return assertions; }
    record AssertionRequestData(int sequenceNo, String type, String definitionJson) { }
}
