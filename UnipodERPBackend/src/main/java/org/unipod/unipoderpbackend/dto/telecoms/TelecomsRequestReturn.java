package org.unipod.unipoderpbackend.dto.telecoms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;

public record TelecomsRequestReturn(
        @NotBlank @Size(max = 2000) @NoNullCharacters String reason
) {
}
