package org.unipod.unipoderpbackend.dto.telecoms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TelecomsRequestMessage(
        @NotBlank @Size(max = 2000) String message
) {
}
