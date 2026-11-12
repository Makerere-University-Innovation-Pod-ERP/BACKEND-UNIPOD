package org.unipod.unipoderpbackend.dto.training;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.training.FollowUpStatus;

/**
 * Omitted {@code notes} keep what is already written; blank notes clear them. {@code version} is the value
 * read with the follow-up: when sent, an edit based on an older one is refused. The frontend's current call
 * does not send it, so it is optional.
 */
public record UpdateFollowUpRequest(
        @NotNull FollowUpStatus status,
        @Size(max = 2000) String notes,
        @Min(0) Long version) {
}
