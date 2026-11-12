package org.unipod.unipoderpbackend.model.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reception_checks")
public class ReceptionCheck {
    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "item_key", nullable = false, length = 64)
    private String itemKey;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(nullable = false)
    private boolean checked;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "checked_by", length = 255)
    private String checkedBy;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReceptionCheck() {
    }

    public ReceptionCheck(
            UUID id,
            UUID submissionId,
            String itemKey,
            String label,
            Instant createdAt
    ) {
        this.id = id;
        this.submissionId = submissionId;
        this.itemKey = itemKey;
        this.label = label;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(boolean checked, String note, String actor, Instant time) {
        this.checked = checked;
        this.note = note;
        if (checked) {
            this.checkedBy = actor;
            this.checkedAt = time;
        } else {
            this.checkedBy = null;
            this.checkedAt = null;
        }
        this.updatedAt = time;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public String getItemKey() {
        return itemKey;
    }

    public String getLabel() {
        return label;
    }

    public boolean isChecked() {
        return checked;
    }

    public String getNote() {
        return note;
    }

    public String getCheckedBy() {
        return checkedBy;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
