package org.unipod.unipoderpbackend.dto.auth;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record PasskeyChallengeResponse(UUID challengeId, JsonNode options) {
}
