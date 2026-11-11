package org.unipod.unipoderpbackend.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_users")
public class ApiUser {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApiUserRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_setup_token_hash", length = 64)
    private String passwordSetupTokenHash;

    @Column(name = "password_setup_expires_at")
    private Instant passwordSetupExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApiUser() {
    }

    public ApiUser(
            UUID id,
            String email,
            String passwordHash,
            ApiUserRole role,
            Instant now
    ) {
        this(id, email, passwordHash, role, false, now);
    }

    public ApiUser(
            UUID id,
            String email,
            String passwordHash,
            ApiUserRole role,
            boolean mustChangePassword,
            Instant now
    ) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
        this.passwordSetupTokenHash = null;
        this.passwordSetupExpiresAt = null;
        this.updatedAt = now;
    }

    public void issuePasswordSetupToken(String tokenHash, Instant expiresAt, Instant now) {
        this.passwordSetupTokenHash = tokenHash;
        this.passwordSetupExpiresAt = expiresAt;
        this.updatedAt = now;
    }

    public boolean hasValidPasswordSetupToken(Instant now) {
        return passwordSetupTokenHash != null
                && passwordSetupExpiresAt != null
                && passwordSetupExpiresAt.isAfter(now);
    }

    public String getPasswordSetupTokenHash() {
        return passwordSetupTokenHash;
    }

    public Instant getPasswordSetupExpiresAt() {
        return passwordSetupExpiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public ApiUserRole getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
