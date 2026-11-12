package org.unipod.unipoderpbackend.dto.training;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.unipod.unipoderpbackend.model.training.FollowUpPriority;

/** {@code priority} defaults to NORMAL and {@code notes} to empty text; the status always starts as PENDING_FOLLOW_UP. */
public record CreateFollowUpRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200) String mentee,
        @NotBlank @Size(max = 200) String mentor,
        @NotNull LocalDate scheduledDate,
        @NotBlank @Size(max = 500) String focus,
        FollowUpPriority priority,
        @Size(max = 2000) String notes) {
}
