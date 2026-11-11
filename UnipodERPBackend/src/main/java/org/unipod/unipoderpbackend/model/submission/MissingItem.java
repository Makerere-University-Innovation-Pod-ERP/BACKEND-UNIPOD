package org.unipod.unipoderpbackend.model.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "missing_items")
public class MissingItem {
    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MissingItemKind kind;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 32)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MissingItemStatus status;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    protected MissingItem() {
    }

    private MissingItem(
            UUID id,
            UUID submissionId,
            MissingItemKind kind,
            String label,
            String note,
            DocumentType documentType,
            String requestedBy,
            Instant requestedAt
    ) {
        this.id = id;
        this.submissionId = submissionId;
        this.kind = kind;
        this.label = label;
        this.note = note;
        this.documentType = documentType;
        this.status = MissingItemStatus.OPEN;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    public static MissingItem information(
            UUID id,
            UUID submissionId,
            String label,
            String note,
            String requestedBy,
            Instant requestedAt
    ) {
        return new MissingItem(
                id, submissionId, MissingItemKind.INFORMATION, label, note, null, requestedBy, requestedAt
        );
    }

    public static MissingItem document(
            UUID id,
            UUID submissionId,
            String label,
            String note,
            DocumentType documentType,
            String requestedBy,
            Instant requestedAt
    ) {
        return new MissingItem(
                id, submissionId, MissingItemKind.DOCUMENT, label, note, documentType, requestedBy, requestedAt
        );
    }

    public void markReminded(Instant time) {
        this.lastRemindedAt = time;
    }

    public void resolve(String actor, String note, Instant time) {
        if (status == MissingItemStatus.RESOLVED) {
            return;
        }
        this.status = MissingItemStatus.RESOLVED;
        this.resolvedBy = actor;
        this.resolvedAt = time;
        this.resolutionNote = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public MissingItemKind getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public String getNote() {
        return note;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public MissingItemStatus getStatus() {
        return status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getLastRemindedAt() {
        return lastRemindedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }
}
