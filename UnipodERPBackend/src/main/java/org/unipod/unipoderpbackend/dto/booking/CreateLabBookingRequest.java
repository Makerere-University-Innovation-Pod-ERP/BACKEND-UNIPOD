package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * The lab desk's form, field for field ({@code date}, {@code start} and {@code end} are Kampala local).
 * {@code bookedBy} is the name of the person the lab is booked for; it defaults to the signed-in user.
 */
public record CreateLabBookingRequest(
        @NotNull LocalDate date,
        @NotNull LocalTime start,
        @NotNull LocalTime end,
        @NotBlank @Size(max = 1000) String purpose,
        @NotNull @Positive Integer groupSize,
        @Size(max = 64) String customerType,
        @Size(max = 200) String bookedBy) {

    // The calendar shows HH:mm, so a time with seconds would be stored differently from what is shown.
    @AssertTrue(message = "Times must be whole minutes.")
    public boolean isWholeMinutes() {
        return (start == null || start.equals(start.truncatedTo(ChronoUnit.MINUTES)))
                && (end == null || end.equals(end.truncatedTo(ChronoUnit.MINUTES)));
    }
}
