package org.unipod.unipoderpbackend.dto.audit;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The optional filters on GET /activity. Any of them may be null.
 * from and to are whole days in Kampala time, both inclusive.
 */
public record ActivityFilter(
        String actor,
        String action,
        String entityType,
        UUID entityId,
        LocalDate from,
        LocalDate to
) {
}
