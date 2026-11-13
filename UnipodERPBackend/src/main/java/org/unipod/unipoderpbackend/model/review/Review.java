package org.unipod.unipoderpbackend.model.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A submitted review. It is immutable once created: decision, comment and submission time
 * are set only through the constructor and have no setters. Later remarks are recorded as
 * {@link ReviewFollowUpNote}s.
 */
@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignment_id", nullable = false, updatable = false, unique = true)
    private Long assignmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private ReviewDecision decision;

    @Column(nullable = false, updatable = false, length = 2000)
    private String comment;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Review() {
    }

    public Review(Long assignmentId, ReviewDecision decision, String comment, Instant submittedAt) {
        this.assignmentId = assignmentId;
        this.decision = decision;
        this.comment = comment;
        this.submittedAt = submittedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getComment() {
        return comment;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Long getVersion() {
        return version;
    }
}
