package org.unipod.unipoderpbackend.dto.telecoms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TelecomsRequestReturn(
        @NotBlank @Size(max = 2000) String reason
) {
}
