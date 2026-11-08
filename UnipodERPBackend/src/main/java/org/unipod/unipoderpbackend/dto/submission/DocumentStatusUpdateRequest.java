package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotNull;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;

public record DocumentStatusUpdateRequest(
        @NotNull DocumentStatus status,
        @NotNull Long version
) {
}
