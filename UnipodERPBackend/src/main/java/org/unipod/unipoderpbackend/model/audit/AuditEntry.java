package org.unipod.unipoderpbackend.model.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;

/**
 * One permanent line in the audit log. Stored, so it is an entity.
 *
 * <p>{@code @Immutable} tells Hibernate never to issue an UPDATE for it, and the
 * database trigger in V25 rejects UPDATE and DELETE as a second wall.</p>
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditEntry {
    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 254)
    private String actor;

    @Column(name = "actor_role", nullable = false, length = 64)
    private String actorRole;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "before_value", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String afterValue;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "request_id", length = 64)
    private String requestId;

    protected AuditEntry() {
    }

    public AuditEntry(
            UUID id,
            Instant occurredAt,
            String actor,
            String actorRole,
            String action,
            String entityType,
            UUID entityId,
            String beforeValue,
            String afterValue,
            UUID departmentId,
            String requestId
    ) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.departmentId = departmentId;
        this.requestId = requestId;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getRequestId() {
        return requestId;
    }
}
