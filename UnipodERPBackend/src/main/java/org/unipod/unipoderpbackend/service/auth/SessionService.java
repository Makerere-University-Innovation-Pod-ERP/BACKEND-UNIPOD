package org.unipod.unipoderpbackend.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.auth.LoginRequest;
import org.unipod.unipoderpbackend.dto.auth.LoginResponse;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.RefreshToken;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.auth.RefreshTokenRepository;

@Service
public class SessionService {
    private static final Duration MAX_REFRESH_TTL = Duration.ofDays(30);

    private final AuthenticationService authentication;
    private final ApiUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;
    private final Duration refreshTtl;

    public SessionService(
            AuthenticationService authentication,
            ApiUserRepository users,
            RefreshTokenRepository refreshTokens,
            Clock clock,
            @Value("${jwt.refresh-token-ttl:P14D}") Duration refreshTtl
    ) {
        if (refreshTtl.isNegative() || refreshTtl.isZero() || refreshTtl.compareTo(MAX_REFRESH_TTL) > 0) {
            throw new IllegalArgumentException("JWT_REFRESH_TOKEN_TTL must be positive and no longer than 30 days.");
        }
        this.authentication = authentication;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        LoginResponse access = authentication.login(request);
        ApiUser user = users.findByEmailIgnoreCase(request.email().trim().toLowerCase(Locale.ROOT)).orElseThrow();
        return withNewRefreshToken(access, user);
    }

    @Transactional
    public LoginResponse issue(ApiUser user) {
        return withNewRefreshToken(authentication.issueToken(user), user);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse refresh(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHashForUpdate(AuthenticationService.hashToken(rawToken.trim()))
                .orElseThrow(SessionService::invalidRefreshToken);
        if (token.isRevoked()) {
            refreshTokens.revokeAllForUser(token.getUserId(), now);
            throw invalidRefreshToken();
        }
        if (!token.isUsable(now)) {
            throw invalidRefreshToken();
        }
        ApiUser user = users.findById(token.getUserId())
                .filter(ApiUser::isEnabled)
                .orElseThrow(SessionService::invalidRefreshToken);
        token.revoke(now);
        refreshTokens.save(token);
        return issue(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHashForUpdate(AuthenticationService.hashToken(rawToken.trim()))
                .ifPresent(token -> {
                    token.revoke(clock.instant());
                    refreshTokens.save(token);
                });
    }

    @Transactional
    public void endAllSessions(UUID userId) {
        refreshTokens.revokeAllForUser(userId, clock.instant());
    }

    private LoginResponse withNewRefreshToken(LoginResponse access, ApiUser user) {
        Instant now = clock.instant();
        String raw = AuthenticationService.passwordSetupToken();
        Instant expiresAt = now.plus(refreshTtl);
        refreshTokens.save(new RefreshToken(UUID.randomUUID(), user.getId(), AuthenticationService.hashToken(raw), expiresAt, now));
        return access.withRefreshToken(raw, expiresAt);
    }

    private static ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The refresh token is invalid or has expired. Sign in again.");
    }
}
