package org.unipod.unipoderpbackend.dto.booking;

import jakarta.validation.constraints.NotNull;

public record SetAttendanceRequest(@NotNull Boolean attended) {
}
