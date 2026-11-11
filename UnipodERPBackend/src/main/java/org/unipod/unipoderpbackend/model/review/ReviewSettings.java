package org.unipod.unipoderpbackend.model.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "review_settings")
public class ReviewSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brief_id", nullable = false, updatable = false)
    private UUID briefId;

    @Column(nullable = false, updatable = false)
    private int round = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewMode mode;

    @Column(name = "reviews_needed", nullable = false)
    private int reviewsNeeded;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "set_by", nullable = false, length = 254)
    private String setBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ReviewSettings() {
    }

    public ReviewSettings(
            UUID briefId,
            int round,
            ReviewMode mode,
            int reviewsNeeded,
            LocalDate dueDate,
            String setBy
    ) {
        this.briefId = briefId;
        this.round = round;
        this.mode = mode;
        this.reviewsNeeded = reviewsNeeded;
        this.dueDate = dueDate;
        this.setBy = setBy;
    }

    @PrePersist
    void markCreated() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void markUpdated() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getBriefId() {
        return briefId;
    }

    public int getRound() {
        return round;
    }

    public ReviewMode getMode() {
        return mode;
    }

    public void setMode(ReviewMode mode) {
        this.mode = mode;
    }

    public int getReviewsNeeded() {
        return reviewsNeeded;
    }

    public void setReviewsNeeded(int reviewsNeeded) {
        this.reviewsNeeded = reviewsNeeded;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getSetBy() {
        return setBy;
    }

    public void setSetBy(String setBy) {
        this.setBy = setBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
