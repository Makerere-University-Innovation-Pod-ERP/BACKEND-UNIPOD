package org.unipod.unipoderpbackend.model.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.training.InvalidTrainingException;

@Entity
@Table(name = "training_sessions")
public class TrainingSession {
    // The frontend does not always send an end time, so a session defaults to a two-hour slot.
    static final Duration DEFAULT_DURATION = Duration.ofHours(2);

    @Id
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String facilitator;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private int fee;

    @Column(name = "safety_course", nullable = false)
    private boolean safetyCourse;

    @Column(nullable = false)
    private boolean published;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingSession() {
    }

    /**
     * @param endsAt optional; defaults to two hours after {@code startsAt}
     * @param fee    whole UGX, 0 for a free session
     */
    public TrainingSession(
            UUID id,
            UUID departmentId,
            String title,
            String facilitator,
            Instant startsAt,
            Instant endsAt,
            int capacity,
            String venue,
            String description,
            int fee,
            boolean safetyCourse,
            boolean published,
            Instant now
    ) {
        if (id == null || departmentId == null || startsAt == null || now == null) {
            throw new InvalidTrainingException("A session needs a department and a start time.");
        }
        Instant end = endsAt == null ? startsAt.plus(DEFAULT_DURATION) : endsAt;
        if (!end.isAfter(startsAt)) {
            throw new InvalidTrainingException("A session must end after it starts.");
        }
        if (capacity <= 0) {
            throw new InvalidTrainingException("Capacity must be at least 1.");
        }
        if (fee < 0) {
            throw new InvalidTrainingException("The fee cannot be negative.");
        }
        this.id = id;
        this.departmentId = departmentId;
        this.title = required(title, "title");
        this.facilitator = required(facilitator, "facilitator");
        this.venue = required(venue, "venue");
        this.description = description == null ? "" : description.strip();
        this.startsAt = startsAt;
        this.endsAt = end;
        this.capacity = capacity;
        this.fee = fee;
        this.safetyCourse = safetyCourse;
        this.published = published;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public TrainingStatus statusAt(Instant now) {
        if (!published) {
            return TrainingStatus.DRAFT;
        }
        if (now.isBefore(startsAt)) {
            return TrainingStatus.UPCOMING;
        }
        if (now.isBefore(endsAt)) {
            return TrainingStatus.IN_PROGRESS;
        }
        return TrainingStatus.COMPLETED;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidTrainingException("The session " + field + " is required.");
        }
        return value.strip();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getTitle() {
        return title;
    }

    public String getFacilitator() {
        return facilitator;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getVenue() {
        return venue;
    }

    public String getDescription() {
        return description;
    }

    public int getFee() {
        return fee;
    }

    public boolean isSafetyCourse() {
        return safetyCourse;
    }

    public boolean isPublished() {
        return published;
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
