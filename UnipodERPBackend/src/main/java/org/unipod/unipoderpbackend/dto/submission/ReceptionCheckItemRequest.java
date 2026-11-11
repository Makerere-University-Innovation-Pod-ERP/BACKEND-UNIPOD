package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReceptionCheckItemRequest(
        @NotBlank @Size(max = 64) String key,
        @NotNull Boolean checked,
        @Size(max = 2000) String note
) {
}
