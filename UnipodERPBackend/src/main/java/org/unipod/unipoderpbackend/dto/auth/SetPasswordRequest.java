package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
        @NotBlank @Size(max = 256) String token,
        @NotBlank @Size(min = 12, max = 72) String newPassword
) {
}
