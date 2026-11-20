package org.unipod.unipoderpbackend.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passkey_credentials")
public class PasskeyCredential {
    @Id
    @Column(name = "credential_id", nullable = false, length = 1024)
    private String credentialId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "public_key_cose", nullable = false, columnDefinition = "bytea")
    private byte[] publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PasskeyCredential() {
    }

    public PasskeyCredential(
            String credentialId,
            UUID userId,
            byte[] publicKeyCose,
            long signatureCount,
            Instant createdAt
    ) {
        this.credentialId = credentialId;
        this.userId = userId;
        this.publicKeyCose = publicKeyCose.clone();
        this.signatureCount = signatureCount;
        this.createdAt = createdAt;
    }

    public void updateSignatureCount(long signatureCount) {
        this.signatureCount = signatureCount;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public UUID getUserId() {
        return userId;
    }

    public byte[] getPublicKeyCose() {
        return publicKeyCose.clone();
    }

    public long getSignatureCount() {
        return signatureCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
