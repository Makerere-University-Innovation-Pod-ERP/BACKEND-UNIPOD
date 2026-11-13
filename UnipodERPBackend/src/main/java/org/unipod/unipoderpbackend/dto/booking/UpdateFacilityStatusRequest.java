package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotNull;
import org.unipod.unipoderpbackend.model.booking.FacilityStatus;

public record UpdateFacilityStatusRequest(
        @NotNull FacilityStatus status
) {
}
