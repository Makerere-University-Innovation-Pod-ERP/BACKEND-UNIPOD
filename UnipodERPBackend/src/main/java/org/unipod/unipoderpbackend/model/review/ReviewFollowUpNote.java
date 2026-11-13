package org.unipod.unipoderpbackend.model.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "review_follow_up_notes")
public class ReviewFollowUpNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false, updatable = false)
    private Long reviewId;

    @Column(nullable = false, updatable = false, length = 1000)
    private String note;

    @Column(name = "created_by", nullable = false, updatable = false, length = 254)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReviewFollowUpNote() {
    }

    public ReviewFollowUpNote(Long reviewId, String note, String createdBy) {
        this.reviewId = reviewId;
        this.note = note;
        this.createdBy = createdBy;
    }

    @PrePersist
    void markCreated() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
