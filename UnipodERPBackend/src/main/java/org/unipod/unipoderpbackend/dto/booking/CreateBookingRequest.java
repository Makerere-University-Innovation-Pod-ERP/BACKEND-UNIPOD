package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID facilityId,
        @NotBlank @Size(max = 1000) String purpose,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Positive int attendees,
        @Size(max = 254) String operator
) {
}
