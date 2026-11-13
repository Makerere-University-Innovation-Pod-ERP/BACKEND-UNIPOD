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
import java.util.UUID;

@Entity
@Table(name = "review_assignments")
public class ReviewAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brief_id", nullable = false, updatable = false)
    private UUID briefId;

    @Column(nullable = false, updatable = false)
    private int round = 1;

    @Column(name = "reviewer_id", nullable = false, updatable = false, length = 254)
    private String reviewerId;

    @Column(length = 100)
    private String department;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReviewAssignmentStatus status = ReviewAssignmentStatus.NOT_STARTED;

    @Column(name = "assigned_by", nullable = false, length = 254)
    private String assignedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ReviewAssignment() {
    }

    public ReviewAssignment(
            UUID briefId,
            int round,
            String reviewerId,
            String department,
            int position,
            String assignedBy
    ) {
        this.briefId = briefId;
        this.round = round;
        this.reviewerId = reviewerId;
        this.department = department;
        this.position = position;
        this.assignedBy = assignedBy;
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

    public String getReviewerId() {
        return reviewerId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getPosition() {
        return position;
    }

    public ReviewAssignmentStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewAssignmentStatus status) {
        this.status = status;
    }

    public String getAssignedBy() {
        return assignedBy;
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
