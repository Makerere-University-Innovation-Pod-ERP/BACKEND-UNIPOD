package org.unipod.unipoderpbackend.dto.training;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.training.AttendanceStatus;

public record AttendanceRequest(@NotNull UUID participantId, @NotNull AttendanceStatus attendance) {
}
