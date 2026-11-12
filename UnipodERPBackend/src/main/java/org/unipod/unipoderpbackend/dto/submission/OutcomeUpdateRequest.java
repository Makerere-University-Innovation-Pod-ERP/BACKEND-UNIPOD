package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record OutcomeUpdateRequest(
        @NotBlank @Size(max = 120) String decision,
        @NotBlank @Size(max = 5000) String note,
        UUID destinationDepartmentId,
        @NotNull @PositiveOrZero Long version
) {
}
