package org.unipod.unipoderpbackend.model.telecoms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "telecoms_request_history")
public class TelecomsRequestHistory {
    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TelecomsRequestStatus status;

    @Column(nullable = false, length = 254)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(length = 2000)
    private String note;

    protected TelecomsRequestHistory() {
    }

    public TelecomsRequestHistory(
            UUID id,
            UUID requestId,
            TelecomsRequestStatus status,
            String actor,
            Instant createdAt,
            String note
    ) {
        this.id = id;
        this.requestId = requestId;
        this.status = status;
        this.actor = actor;
        this.createdAt = createdAt;
        this.note = note;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public TelecomsRequestStatus getStatus() { return status; }
    public String getActor() { return actor; }
    public Instant getCreatedAt() { return createdAt; }
    public String getNote() { return note; }
}
