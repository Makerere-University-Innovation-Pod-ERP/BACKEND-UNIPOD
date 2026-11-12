package org.unipod.unipoderpbackend.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.unipod.unipoderpbackend.dto.auth.ApiUserResponse;
import org.unipod.unipoderpbackend.dto.auth.ChangePasswordRequest;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.dto.auth.LoginRequest;
import org.unipod.unipoderpbackend.dto.auth.LoginResponse;
import org.unipod.unipoderpbackend.dto.auth.SetPasswordRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;

@Service
public class AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final Duration MAX_TOKEN_TTL = Duration.ofHours(1);
    private static final Duration PASSWORD_SETUP_TTL = Duration.ofHours(72);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final String TEMPORARY_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";
    private static final int TEMPORARY_PASSWORD_LENGTH = 16;
    private static final int PASSWORD_SETUP_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Duration tokenTtl;
    private final NotificationOutbox notificationOutbox;
    private final String publicBaseUrl;

    /**
     * Test-friendly constructor used where password setup delivery is not
     * exercised.
     */
    public AuthenticationService(
            ApiUserRepository users,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            Clock clock,
            String issuer,
            String audience,
            Duration tokenTtl
    ) {
        this(users, passwordEncoder, jwtEncoder, clock, issuer, audience, tokenTtl, null, "");
    }

    @Autowired
    public AuthenticationService(
            ApiUserRepository users,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${jwt.issuer-uri}") String issuer,
            @Value("${jwt.audience}") String audience,
            @Value("${jwt.access-token-ttl}") Duration tokenTtl,
            NotificationOutbox notificationOutbox,
            @Value("${app.public-base-url:}") String publicBaseUrl
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
        this.notificationOutbox = notificationOutbox;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
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
        return issueToken(user);
    }

    public LoginResponse issueToken(ApiUser user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(tokenTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of(user.getRole().name()))
                .claim("mustChangePassword", user.isMustChangePassword())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();
        return new LoginResponse("Bearer", token, expiresAt, user.isMustChangePassword());
    }

    /**
     * Creates the submitter account used by an assisted (walk-in) submission.
     * A new account receives a one-time temporary password and a password setup
     * link; an existing account is reused without exposing either.
     */
    @Transactional
    public AssistedAccount createAssistedSubmitter(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            return new AssistedAccount(normalizedEmail, false, null, null, null);
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
        String setupToken = passwordSetupToken();
        Instant setupExpiresAt = now.plus(PASSWORD_SETUP_TTL);
        user.issuePasswordSetupToken(hashToken(setupToken), setupExpiresAt, now);
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
        String setPasswordUrl = passwordSetupUrl(setupToken);
        if (notificationOutbox != null && setPasswordUrl != null) {
            notificationOutbox.recordPasswordSetup(
                    user.getId(),
                    normalizedEmail,
                    setPasswordUrl,
                    setupExpiresAt,
                    now
            );
        }
        return new AssistedAccount(
                normalizedEmail,
                true,
                temporaryPassword,
                setupToken,
                setPasswordUrl
        );
    }

    /**
     * Completes the password setup flow from the one-time link. The token is
     * single use: a successful change clears it.
     */
    @Transactional
    public void setPassword(SetPasswordRequest request) {
        String tokenHash = hashToken(request.token().trim());
        Instant now = clock.instant();
        ApiUser user = users.findByPasswordSetupTokenHash(tokenHash)
                .filter(ApiUser::isEnabled)
                .filter(candidate -> candidate.hasValidPasswordSetupToken(now))
                .orElseThrow(AuthenticationService::invalidSetupToken);
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The new password must be different from the temporary password."
            );
        }
        int updated = users.consumePasswordSetupToken(
                tokenHash,
                passwordEncoder.encode(request.newPassword()),
                now
        );
        if (updated != 1) {
            throw invalidSetupToken();
        }
    }

    @Transactional
    public void requestPasswordReset(String email) {
        ApiUser user = users.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(ApiUser::isEnabled)
                .orElse(null);
        if (user == null) {
            return;
        }
        Instant now = clock.instant();
        String resetToken = passwordSetupToken();
        Instant expiresAt = now.plus(PASSWORD_RESET_TTL);
        user.issuePasswordSetupToken(hashToken(resetToken), expiresAt, now);
        users.save(user);
        if (notificationOutbox != null) {
            notificationOutbox.recordPasswordReset(
                    user.getId(),
                    user.getEmail(),
                    passwordSetupUrl(resetToken),
                    expiresAt,
                    now
            );
        }
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

    private static ResponseStatusException invalidSetupToken() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The password setup link is invalid or has expired."
        );
    }

    private String passwordSetupUrl(String token) {
        String base = publicBaseUrl.isBlank() ? requestBaseUrl() : publicBaseUrl;
        if (base == null || base.isBlank()) {
            throw new IllegalStateException(
                    "app.public-base-url must be configured (APP_PUBLIC_BASE_URL) "
                            + "before issuing password-setup links."
            );
        }
        String normalized = base.endsWith("/")
                ? base.substring(0, base.length() - 1)
                : base;
        return normalized + "/set-password?token=" + token;
    }

    private static String requestBaseUrl() {
        UriComponents components;
        try {
            components = ServletUriComponentsBuilder.fromCurrentContextPath().build();
        } catch (IllegalStateException noRequest) {
            return null;
        }
        if (components.getScheme() == null || components.getHost() == null) {
            return null;
        }
        StringBuilder base = new StringBuilder(components.getScheme())
                .append("://")
                .append(components.getHost());
        if (components.getPort() != -1) {
            base.append(':').append(components.getPort());
        }
        log.warn(
                "APP_PUBLIC_BASE_URL is not set; password-setup links will use the request origin {}. "
                        + "Configure the public frontend URL to link submitters to the portal.",
                base
        );
        return base.toString();
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

    private static String passwordSetupToken() {
        byte[] bytes = new byte[PASSWORD_SETUP_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
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

    public record AssistedAccount(
            String email,
            boolean created,
            String temporaryPassword,
            String setPasswordToken,
            String setPasswordUrl
    ) {
        public AssistedAccount(String email, boolean created, String temporaryPassword) {
            this(email, created, temporaryPassword, null, null);
        }
    }
}
