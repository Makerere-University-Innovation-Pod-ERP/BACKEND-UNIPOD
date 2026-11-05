package org.unipod.unipoderpbackend.submissions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class SubmissionDocument {
    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DocumentStatus status;

    @Column(name = "file_reference", length = 256)
    private String fileReference;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SubmissionDocument() {
    }

    public SubmissionDocument(
            UUID id,
            UUID submissionId,
            String name,
            DocumentStatus status,
            String fileReference,
            String createdBy,
            Instant createdAt
    ) {
        this.id = id;
        this.submissionId = submissionId;
        this.name = name;
        this.status = status;
        this.fileReference = fileReference;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public String getName() {
        return name;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getFileReference() {
        return fileReference;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
