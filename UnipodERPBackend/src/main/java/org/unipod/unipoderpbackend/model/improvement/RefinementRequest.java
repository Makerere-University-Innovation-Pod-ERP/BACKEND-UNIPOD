package org.unipod.unipoderpbackend.model.improvement;

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

/**
 * One "ask for changes" round on a brief. Everything describing the request is fixed at creation;
 * only {@code status} and {@code respondedAt} change, when the submitter answers with a revision.
 * {@code guidanceNote} is the only text the submitter will see.
 */
@Entity
@Table(name = "refinement_requests")
public class RefinementRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brief_id", nullable = false, updatable = false)
    private UUID briefId;

    @Column(nullable = false, updatable = false)
    private int cycle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private RefinementSource source;

    @Column(name = "guidance_note", nullable = false, updatable = false, length = 2000)
    private String guidanceNote;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RefinementStatus status = RefinementStatus.OPEN;

    @Column(name = "requested_by", nullable = false, updatable = false, length = 254)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected RefinementRequest() {
    }

    public RefinementRequest(
            UUID briefId,
            int cycle,
            RefinementSource source,
            String guidanceNote,
            LocalDate dueDate,
            String requestedBy,
            Instant requestedAt
    ) {
        this.briefId = briefId;
        this.cycle = cycle;
        this.source = source;
        this.guidanceNote = guidanceNote;
        this.dueDate = dueDate;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
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

    public int getCycle() {
        return cycle;
    }

    public RefinementSource getSource() {
        return source;
    }

    public String getGuidanceNote() {
        return guidanceNote;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public RefinementStatus getStatus() {
        return status;
    }

    public void setStatus(RefinementStatus status) {
        this.status = status;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
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
