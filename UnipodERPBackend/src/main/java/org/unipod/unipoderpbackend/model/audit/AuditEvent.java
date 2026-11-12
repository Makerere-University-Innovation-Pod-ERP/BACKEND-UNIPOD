package org.unipod.unipoderpbackend.model.audit;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.unipod.unipoderpbackend.model.authorization.Actor;

/**
 * What a module hands to {@code AuditService.log(...)}. Not stored itself;
 * the service turns it into an {@link AuditEntry}.
 *
 * @param actor        who did it
 * @param action       what happened, lower case with dots, e.g. "user.created", "submission.forwarded"
 * @param entityType   what kind of record, e.g. "user", "submission"
 * @param entityId     which record (null if not about one record)
 * @param before       the record before the change (null on create); any object Jackson can write
 * @param after        the record after the change (null on delete)
 * @param departmentId the department the record belongs to, if any
 */
public record AuditEvent(
        Actor actor,
        String action,
        String entityType,
        UUID entityId,
        Object before,
        Object after,
        UUID departmentId
) {
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");

    public AuditEvent {
        Objects.requireNonNull(actor, "actor");
        action = requireName(action, "action");
        entityType = requireName(entityType, "entityType");
    }

    public static AuditEvent created(Actor actor, String action, String entityType, UUID entityId, Object after) {
        return new AuditEvent(actor, action, entityType, entityId, null, after, null);
    }

    public static AuditEvent changed(
            Actor actor, String action, String entityType, UUID entityId, Object before, Object after) {
        return new AuditEvent(actor, action, entityType, entityId, before, after, null);
    }

    private static String requireName(String value, String field) {
        if (value == null || value.length() > 64 || !NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lower case words joined by dots, at most 64 characters");
        }
        return value;
    }
}
