package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddAttendeeRequest(@NotBlank @Size(max = 200) String name) {
}
