package org.unipod.unipoderpbackend.model.improvement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * The submitter's improved version of a brief, answering exactly one {@link RefinementRequest}.
 * The original brief is version 1, so revisions start at 2. Immutable: constructor only.
 */
@Entity
@Table(name = "case_revisions")
public class CaseRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brief_id", nullable = false, updatable = false)
    private UUID briefId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "request_id", nullable = false, updatable = false, unique = true)
    private Long requestId;

    @Column(name = "what_changed", nullable = false, updatable = false, length = 2000)
    private String whatChanged;

    @Column(name = "submitted_by", nullable = false, updatable = false, length = 254)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected CaseRevision() {
    }

    public CaseRevision(
            UUID briefId,
            int versionNumber,
            Long requestId,
            String whatChanged,
            String submittedBy,
            Instant submittedAt
    ) {
        this.briefId = briefId;
        this.versionNumber = versionNumber;
        this.requestId = requestId;
        this.whatChanged = whatChanged;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getBriefId() {
        return briefId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getWhatChanged() {
        return whatChanged;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Long getVersion() {
        return version;
    }
}
