package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The workshop screen's cancel dialog: a reason is required. */
public record CancelTextileBookingRequest(@NotBlank @Size(max = 500) String reason) {
}
