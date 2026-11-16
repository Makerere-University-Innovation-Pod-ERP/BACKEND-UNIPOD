package org.unipod.unipoderpbackend.dto.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.unipod.unipoderpbackend.model.submission.CaseStage;

public record CaseTransitionRequest(
        @NotNull @JsonProperty("to_stage") CaseStage toStage,
        @Size(max = 2000) String note,
        LocalDate dueDate,
        @Size(max = 50) List<@Size(max = 500) String> requestedChanges,
        @NotNull @PositiveOrZero Long version
) {
}
