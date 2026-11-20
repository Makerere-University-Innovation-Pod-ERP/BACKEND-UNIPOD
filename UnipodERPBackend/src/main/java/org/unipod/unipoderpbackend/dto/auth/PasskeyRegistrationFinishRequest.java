package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record PasskeyRegistrationFinishRequest(
        @NotNull UUID challengeId,
        @NotNull JsonNode credential,
        @NotBlank @Size(max = 72) String password
) {
    public PasskeyRegistrationFinishRequest {
        if (credential != null && credential.isNull()) {
            credential = null;
        }
    }
}
