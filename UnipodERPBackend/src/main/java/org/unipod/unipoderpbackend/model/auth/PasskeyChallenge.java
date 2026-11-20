package org.unipod.unipoderpbackend.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passkey_challenges")
public class PasskeyChallenge {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String ceremony;

    @Column(name = "request_json", nullable = false, columnDefinition = "text")
    private String requestJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasskeyChallenge() {
    }

    public PasskeyChallenge(
            UUID id,
            UUID userId,
            String ceremony,
            String requestJson,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.ceremony = ceremony;
        this.requestJson = requestJson;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCeremony() {
        return ceremony;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
