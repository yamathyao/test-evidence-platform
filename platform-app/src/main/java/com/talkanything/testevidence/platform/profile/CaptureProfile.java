package com.talkanything.testevidence.platform.profile;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "capture_profile", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "version"}))
public class CaptureProfile {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private int version;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    private String definitionJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaptureProfile() { }

    CaptureProfile(String name, int version, String definitionJson) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.version = version;
        this.definitionJson = definitionJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getVersion() { return version; }
    public String getDefinitionJson() { return definitionJson; }
    public Instant getCreatedAt() { return createdAt; }
}
