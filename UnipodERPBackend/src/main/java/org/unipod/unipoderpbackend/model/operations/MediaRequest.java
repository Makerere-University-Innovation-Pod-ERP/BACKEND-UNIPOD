package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.unipod.unipoderpbackend.exception.media.MediaConflictException;
import org.unipod.unipoderpbackend.exception.operations.OperationsConflictException;
import org.unipod.unipoderpbackend.model.media.MediaBoardStatus;

@Entity
@Table(name = "media_requests")
public class MediaRequest {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(name = "brief_id")
    private UUID briefId;
    @Column(name = "department_order_id")
    private UUID departmentOrderId;
    @Column(name = "submitter_id", nullable = false, length = 254)
    private String submitterId;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, length = 32)
    private String status = "IN_PRODUCTION";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_payload", columnDefinition = "jsonb")
    private String deliveryPayload;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "delivered_by", length = 254)
    private String deliveredBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "studio_id")
    private UUID studioId;
    @Column(length = 200)
    private String assignee;
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
    @Column(length = 2000)
    private String description;

    protected MediaRequest() {}
    public MediaRequest(UUID id, String code, UUID briefId, UUID departmentOrderId, String submitterId, String title, Instant now) {
        this.id = id; this.code = code; this.briefId = briefId; this.departmentOrderId = departmentOrderId;
        this.submitterId = submitterId; this.title = title; this.createdAt = now; this.updatedAt = now;
    }
    public void deliver(String payload, String actor, Instant now) {
        this.deliveryPayload = payload; this.deliveredAt = now; this.deliveredBy = actor;
        this.status = "DELIVERED"; this.updatedAt = now;
    }
    public void complete(Instant now) {
        if (!"DELIVERED".equals(status)) {
            throw new OperationsConflictException("INVALID_MEDIA_REQUEST_TRANSITION",
                    "Only a delivered media request can be completed.");
        }
        this.status = "COMPLETED";
        this.updatedAt = now;
    }
    /**
     * One step forward on the media board (D-039): REQUESTED, SCHEDULED, IN_PRODUCTION, DELIVERED.
     * Skipping a step, repeating one or going back is a conflict, and so is moving a cancelled request.
     */
    public void moveTo(MediaBoardStatus target, String actor, Instant now) {
        MediaBoardStatus current = MediaBoardStatus.shown(status)
                .orElseThrow(() -> new MediaConflictException("A cancelled request cannot be moved."));
        MediaBoardStatus expected = switch (current) {
            case REQUESTED -> MediaBoardStatus.SCHEDULED;
            case SCHEDULED -> MediaBoardStatus.IN_PRODUCTION;
            case IN_PRODUCTION -> MediaBoardStatus.DELIVERED;
            case DELIVERED -> throw new MediaConflictException("A delivered request cannot be moved.");
        };
        if (target != expected) {
            throw new MediaConflictException(
                    "A " + label(current) + " request can only move to " + label(expected) + ".");
        }
        if (target == MediaBoardStatus.DELIVERED) {
            deliver(null, actor, now);
        } else {
            this.status = target.name();
            this.updatedAt = now;
        }
    }

    /** Refuses a request that is finished or cancelled; callers check it before validating a change. */
    public void requireEditable() {
        if (MediaBoardStatus.shown(status).filter(shown -> shown != MediaBoardStatus.DELIVERED).isEmpty()) {
            throw new MediaConflictException("A request that is finished or cancelled cannot be changed.");
        }
    }

    /** Sets the four board fields as given; a delivered, completed or cancelled request is closed to edits. */
    public void assign(UUID studioId, String assignee, Instant scheduledAt, String description, Instant now) {
        requireEditable();
        this.studioId = studioId;
        this.assignee = assignee;
        this.scheduledAt = scheduledAt;
        this.description = description;
        this.updatedAt = now;
    }

    private static String label(MediaBoardStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public UUID getStudioId() { return studioId; }
    public String getAssignee() { return assignee; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getDescription() { return description; }
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public UUID getBriefId() { return briefId; }
    public UUID getDepartmentOrderId() { return departmentOrderId; }
    public String getSubmitterId() { return submitterId; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getDeliveryPayload() { return deliveryPayload; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public String getDeliveredBy() { return deliveredBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

