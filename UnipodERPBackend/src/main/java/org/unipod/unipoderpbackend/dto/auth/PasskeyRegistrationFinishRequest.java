package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record PasskeyRegistrationFinishRequest(
        @NotNull UUID challengeId,
        @NotNull JsonNode credential,
        @NotBlank String password
) {
    public PasskeyRegistrationFinishRequest {
        if (credential != null && credential.isNull()) {
            credential = null;
        }
    }
}
