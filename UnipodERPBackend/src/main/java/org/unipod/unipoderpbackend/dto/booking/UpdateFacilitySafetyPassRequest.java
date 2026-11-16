package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotNull;

public record UpdateFacilitySafetyPassRequest(
        @NotNull Boolean requiresSafetyPass
) {
}
