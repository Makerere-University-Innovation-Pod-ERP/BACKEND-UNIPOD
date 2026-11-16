package org.unipod.unipoderpbackend.dto.operations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.operations.TimetableBlock;

public record TimetableBlockResponse(
        UUID id, UUID facility_id, String department, String title,
        Instant starts_at, Instant ends_at, String recurrence_rule,
        LocalDate recurrence_until, String source
) {
    public static TimetableBlockResponse from(TimetableBlock b) {
        return new TimetableBlockResponse(b.getId(), b.getFacilityId(), b.getDepartment(), b.getTitle(),
                b.getStartsAt(), b.getEndsAt(), b.getRecurrenceRule(), b.getRecurrenceUntil(), b.getSource());
    }
}

