package org.unipod.unipoderpbackend.dto.audit;

import java.time.LocalDate;
import java.util.UUID;

public record ActivityFilter(
        String actor,
        String action,
        String entityType,
        UUID entityId,
        LocalDate from,
        LocalDate to
) {
}
