package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.booking.FacilityStatus;

public record CreateFacilityRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 160) String location,
        @Positive int capacity,
        @NotNull FacilityStatus status,
        @Size(max = 100) String department
) {
}
