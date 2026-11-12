package org.unipod.unipoderpbackend.model.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.training.InvalidTrainingException;
import org.unipod.unipoderpbackend.exception.training.TrainingConflictException;

@Entity
@Table(name = "mentorship_follow_ups")
public class MentorshipFollowUp {
    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String mentee;

    @Column(nullable = false, length = 200)
    private String mentor;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false, length = 500)
    private String focus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowUpStatus status;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FollowUpPriority priority;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MentorshipFollowUp() {
    }

    /**
     * A new follow-up always starts as PENDING_FOLLOW_UP.
     *
     * @param priority optional; defaults to NORMAL
     * @param notes    optional; defaults to empty text
     */
    public MentorshipFollowUp(
            UUID id,
            String title,
            String mentee,
            String mentor,
            LocalDate scheduledDate,
            String focus,
            FollowUpPriority priority,
            String notes,
            Instant now
    ) {
        if (scheduledDate == null) {
            throw new InvalidTrainingException("A follow-up needs a scheduled date.");
        }
        if (id == null || now == null) {
            throw new InvalidTrainingException("A follow-up needs an id and a creation time.");
        }
        this.id = id;
        this.title = required(title, "title");
        this.mentee = required(mentee, "mentee");
        this.mentor = required(mentor, "mentor");
        this.focus = required(focus, "focus");
        this.scheduledDate = scheduledDate;
        this.priority = priority == null ? FollowUpPriority.NORMAL : priority;
        this.notes = notes == null ? "" : notes.strip();
        this.status = FollowUpStatus.PENDING_FOLLOW_UP;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Moves the follow-up to a new status. Notes are replaced only when given, so a status-only
     * PATCH keeps what is already written. A completed follow-up is closed for good.
     */
    public void update(FollowUpStatus newStatus, String newNotes, Instant now) {
        if (newStatus == null) {
            throw new InvalidTrainingException("A follow-up status is required.");
        }
        if (status == FollowUpStatus.COMPLETED) {
            throw new TrainingConflictException("This follow-up is already completed.");
        }
        status = newStatus;
        if (newNotes != null) {
            notes = newNotes.strip();
        }
        updatedAt = now;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidTrainingException("The follow-up " + field + " is required.");
        }
        return value.strip();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMentee() {
        return mentee;
    }

    public String getMentor() {
        return mentor;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public String getFocus() {
        return focus;
    }

    public FollowUpStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public FollowUpPriority getPriority() {
        return priority;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
