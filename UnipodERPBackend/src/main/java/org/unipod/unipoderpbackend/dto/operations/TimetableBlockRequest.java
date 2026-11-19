package org.unipod.unipoderpbackend.dto.operations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TimetableBlockRequest(
        UUID facility_id,
        String department,
        @NotBlank String title,
        @NotNull Instant starts_at,
        @NotNull Instant ends_at,
        String recurrence_rule,
        LocalDate recurrence_until,
        String source
) {}

