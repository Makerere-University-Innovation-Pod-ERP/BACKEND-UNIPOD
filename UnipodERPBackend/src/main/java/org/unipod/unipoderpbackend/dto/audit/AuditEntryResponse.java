package org.unipod.unipoderpbackend.dto.audit;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** One audit line as the API returns it. before and after are real JSON objects, not strings. */
public record AuditEntryResponse(
        UUID id,
        Instant occurredAt,
        String actor,
        String actorRole,
        String action,
        String entityType,
        UUID entityId,
        JsonNode before,
        JsonNode after,
        UUID departmentId,
        String requestId
) {
}
