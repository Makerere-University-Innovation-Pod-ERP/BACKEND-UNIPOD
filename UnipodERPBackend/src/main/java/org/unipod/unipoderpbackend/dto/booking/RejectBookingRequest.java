package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectBookingRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
