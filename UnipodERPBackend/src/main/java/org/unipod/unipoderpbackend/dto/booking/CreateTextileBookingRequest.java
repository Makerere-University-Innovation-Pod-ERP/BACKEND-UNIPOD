package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The workshop desk's form, field for field ({@code date}, {@code start} and {@code end} are Kampala
 * local). {@code capacity} is the number of places of a TRAINING session and must be left out for a
 * WORKSHOP. {@code machineId} is kept as given: machines belong to the equipment feature (D-048).
 */
public record CreateTextileBookingRequest(
        @NotNull TextileKind kind,
        @NotNull LocalDate date,
        @NotNull LocalTime start,
        @NotNull LocalTime end,
        @NotBlank @Size(max = 1000) String purpose,
        @Size(max = 64) String machineId,
        @Positive Integer capacity) {
}
