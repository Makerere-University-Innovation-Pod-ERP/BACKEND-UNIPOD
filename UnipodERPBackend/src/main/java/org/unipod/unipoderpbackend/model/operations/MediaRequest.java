package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.unipod.unipoderpbackend.exception.operations.OperationsConflictException;

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

