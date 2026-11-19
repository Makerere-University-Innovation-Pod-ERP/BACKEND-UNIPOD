package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record RefinementRequestAskRequest(
        @NotBlank @Size(max = 2000) String guidanceNote,
        @NotNull LocalDate dueDate,
        @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 500) String> requestedChanges,
        @NotNull @PositiveOrZero Long version
) {
}
