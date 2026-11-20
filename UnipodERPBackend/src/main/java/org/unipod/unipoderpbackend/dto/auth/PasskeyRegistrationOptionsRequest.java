package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasskeyRegistrationOptionsRequest(@NotBlank @Size(max = 72) String password) {
}
