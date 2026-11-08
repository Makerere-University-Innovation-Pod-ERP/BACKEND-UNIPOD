package org.unipod.unipoderpbackend.dto.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.submission.CaseStage;

public record CaseTransitionRequest(
        @NotNull @JsonProperty("to_stage") CaseStage toStage,
        @Size(max = 2000) String note,
        @NotNull @PositiveOrZero Long version
) {
}
