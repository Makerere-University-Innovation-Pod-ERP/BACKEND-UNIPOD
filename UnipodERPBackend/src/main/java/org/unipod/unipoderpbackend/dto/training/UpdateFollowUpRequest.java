package org.unipod.unipoderpbackend.dto.training;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.training.FollowUpStatus;

/** Omitted {@code notes} keep what is already written; blank notes clear them. */
public record UpdateFollowUpRequest(@NotNull FollowUpStatus status, @Size(max = 2000) String notes) {
}
