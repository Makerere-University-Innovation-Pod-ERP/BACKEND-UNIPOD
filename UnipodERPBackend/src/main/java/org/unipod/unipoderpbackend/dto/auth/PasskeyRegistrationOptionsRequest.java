package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record PasskeyRegistrationOptionsRequest(@NotBlank String password) {
}
