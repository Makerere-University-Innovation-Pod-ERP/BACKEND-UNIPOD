package org.unipod.unipoderpbackend.model.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_blobs")
public class DocumentBlob {
    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentBlob() {
    }

    public DocumentBlob(UUID id, byte[] content, Instant createdAt) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public byte[] getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
