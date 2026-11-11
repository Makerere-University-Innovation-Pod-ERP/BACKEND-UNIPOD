package org.unipod.unipoderpbackend.service.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.auth.ApiUserResponse;
import org.unipod.unipoderpbackend.dto.auth.ChangePasswordRequest;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.dto.auth.LoginRequest;
import org.unipod.unipoderpbackend.dto.auth.LoginResponse;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;

@Service
public class AuthenticationService {
    private static final Duration MAX_TOKEN_TTL = Duration.ofHours(1);
    private static final String TEMPORARY_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";
    private static final int TEMPORARY_PASSWORD_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Duration tokenTtl;

    public AuthenticationService(
            ApiUserRepository users,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${jwt.issuer-uri}") String issuer,
            @Value("${jwt.audience}") String audience,
            @Value("${jwt.access-token-ttl}") Duration tokenTtl
    ) {
        if (tokenTtl.isNegative() || tokenTtl.isZero() || tokenTtl.compareTo(MAX_TOKEN_TTL) > 0) {
            throw new IllegalArgumentException("JWT_ACCESS_TOKEN_TTL must be positive and no longer than one hour.");
        }
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
        this.tokenTtl = tokenTtl;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        ApiUser user = users.findByEmailIgnoreCase(email)
                .filter(ApiUser::isEnabled)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password."
                ));

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(tokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
        return new LoginResponse("Bearer", token, expiresAt, user.isMustChangePassword());
    }

    @Transactional
    public AssistedAccount createAssistedSubmitter(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            return new AssistedAccount(normalizedEmail, false, null);
        }

        String temporaryPassword = temporaryPassword();
        Instant now = clock.instant();
        ApiUser user = new ApiUser(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(temporaryPassword),
                ApiUserRole.SUBMITTER,
                true,
                now
        );
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Another request created the account first. Roll back this attempt; a retry
            // will find the existing account and reuse it without exposing a password.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email was created concurrently. Retry the assisted submission."
            );
        }
        return new AssistedAccount(normalizedEmail, true, temporaryPassword);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        ApiUser user = users.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(ApiUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "A valid account is required."
                ));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The new password must be different from the current password."
            );
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()), clock.instant());
        users.save(user);
    }

    private static String temporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(TEMPORARY_PASSWORD_ALPHABET.charAt(
                    RANDOM.nextInt(TEMPORARY_PASSWORD_ALPHABET.length())
            ));
        }
        return password.toString();
    }

    @Transactional
    public ApiUserResponse createUser(CreateApiUserRequest request) {
        String email = normalizeEmail(request.email());
        if (request.password().length() < 12 || request.password().length() > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be between 12 and 72 characters."
            );
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        Instant now = clock.instant();
        ApiUser user = new ApiUser(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                request.role(),
                now
        );
        try {
            return ApiUserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists.",
                    exception
            );
        }
    }

    @Transactional
    public void bootstrapAdministrator(String email, String password) {
        if (users.count() > 0) {
            return;
        }
        createUser(new CreateApiUserRequest(email, password, ApiUserRole.ADMINISTRATOR));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record AssistedAccount(String email, boolean created, String temporaryPassword) {
    }
}
