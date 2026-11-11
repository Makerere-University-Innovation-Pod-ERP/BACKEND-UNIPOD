package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectSubmissionRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
