package org.unipod.unipoderpbackend.dto.auth;

import java.time.Instant;

public record LoginResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt
) {
}
