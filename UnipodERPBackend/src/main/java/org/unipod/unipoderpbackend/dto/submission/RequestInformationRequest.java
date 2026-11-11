package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestInformationRequest(
        @NotBlank @Size(max = 2000) String note
) {
}
