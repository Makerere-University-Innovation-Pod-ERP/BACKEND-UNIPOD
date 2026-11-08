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
@Table(name = "case_stage_history")
public class CaseStageHistory {
    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 32)
    private SubmissionStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false, length = 32)
    private SubmissionStage toStage;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected CaseStageHistory() {
    }

    public CaseStageHistory(
            UUID id,
            UUID submissionId,
            SubmissionStage fromStage,
            SubmissionStage toStage,
            String changedBy,
            Instant changedAt
    ) {
        this(id, submissionId, fromStage, toStage, changedBy, null, changedAt);
    }

    public CaseStageHistory(
            UUID id,
            UUID submissionId,
            SubmissionStage fromStage,
            SubmissionStage toStage,
            String changedBy,
            String note,
            Instant changedAt
    ) {
        this.id = id;
        this.submissionId = submissionId;
        this.fromStage = fromStage;
        this.toStage = toStage;
        this.changedBy = changedBy;
        this.note = note;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public SubmissionStage getFromStage() {
        return fromStage;
    }

    public SubmissionStage getToStage() {
        return toStage;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
