package org.unipod.unipoderpbackend.dto.auth;

import java.time.Instant;

public record LoginResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        boolean mustChangePassword,
        String refreshToken,
        Instant refreshExpiresAt
) {
    public LoginResponse(String tokenType, String accessToken, Instant expiresAt, boolean mustChangePassword) {
        this(tokenType, accessToken, expiresAt, mustChangePassword, null, null);
    }

    public LoginResponse(String tokenType, String accessToken, Instant expiresAt) {
        this(tokenType, accessToken, expiresAt, false);
    }

    public LoginResponse withRefreshToken(String token, Instant tokenExpiresAt) {
        return new LoginResponse(tokenType, accessToken, expiresAt, mustChangePassword, token, tokenExpiresAt);
    }
}
