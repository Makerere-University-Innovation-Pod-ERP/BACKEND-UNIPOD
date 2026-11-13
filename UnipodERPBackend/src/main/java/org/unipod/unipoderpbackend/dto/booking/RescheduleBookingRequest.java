package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleBookingRequest(
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
