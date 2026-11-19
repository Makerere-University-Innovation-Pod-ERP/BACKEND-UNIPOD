package org.unipod.unipoderpbackend.dto.training;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The roster the screen saves: {@code { attendance: [{participantId, status}] }}. */
public record SaveBootcampAttendanceRequest(@NotNull @Size(max = 500) List<@Valid @NotNull Entry> attendance) {
    public record Entry(@NotNull UUID participantId, @NotNull BootcampAttendance status) {
    }
}
