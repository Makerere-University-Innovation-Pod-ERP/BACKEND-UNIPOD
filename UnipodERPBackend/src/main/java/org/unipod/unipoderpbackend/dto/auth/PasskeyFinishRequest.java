package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record PasskeyFinishRequest(
        @NotNull UUID challengeId,
        @NotNull JsonNode credential
) {
}
