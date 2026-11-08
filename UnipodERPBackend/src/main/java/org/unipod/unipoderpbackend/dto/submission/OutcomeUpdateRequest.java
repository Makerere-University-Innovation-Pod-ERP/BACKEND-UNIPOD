package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OutcomeUpdateRequest(
        @Size(max = 120) String decision,
        @Size(max = 5000) String note,
        @NotNull Long version
) {
}
