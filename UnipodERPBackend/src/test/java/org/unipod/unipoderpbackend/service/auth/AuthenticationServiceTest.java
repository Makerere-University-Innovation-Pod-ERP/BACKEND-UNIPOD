package org.unipod.unipoderpbackend.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.auth.ChangePasswordRequest;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.dto.auth.LoginRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T11:00:00Z");
    private static final String PASSWORD = "correct horse battery staple";

    private final ApiUserRepository users = mock(ApiUserRepository.class);
    private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private AuthenticationService authentication;

    @BeforeEach
    void setUp() {
        authentication = new AuthenticationService(
                users,
                passwordEncoder,
                jwtEncoder,
                clock,
                "https://api.example.test",
                "unipod-erp-api",
                Duration.ofMinutes(15)
        );
    }

    @Test
    void loginIssuesShortLivedBearerTokenForEnabledAccount() {
        ApiUser user = user("admin@example.test", ApiUserRole.ADMINISTRATOR);
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(user));
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .claim("sub", "admin@example.test")
                .issuedAt(NOW)
                .expiresAt(NOW.plus(Duration.ofMinutes(15)))
                .build());

        var response = authentication.login(new LoginRequest(" Admin@Example.Test ", PASSWORD));

        assertEquals("Bearer", response.tokenType());
        assertEquals("signed-token", response.accessToken());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), response.expiresAt());
        verify(jwtEncoder).encode(any(JwtEncoderParameters.class));
    }

    @Test
    void loginRejectsInvalidCredentialsWithGenericUnauthorizedResponse() {
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authentication.login(new LoginRequest("admin@example.test", PASSWORD))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void createUserStoresHashAndReturnsOnlyPublicAccountFields() {
        when(users.existsByEmailIgnoreCase("reviewer@example.test")).thenReturn(false);
        when(users.saveAndFlush(any(ApiUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authentication.createUser(new CreateApiUserRequest(
                "Reviewer@Example.Test",
                PASSWORD,
                ApiUserRole.REVIEWER
        ));

        assertEquals("reviewer@example.test", response.email());
        assertEquals(ApiUserRole.REVIEWER, response.role());
        verify(users).saveAndFlush(argThat(user ->
                !PASSWORD.equals(user.getPasswordHash())
                        && user.getPasswordHash().length() <= 100
                        && user.getRole() == ApiUserRole.REVIEWER
        ));
    }

    @Test
    void loginReportsThatAnAssistedAccountMustChangeItsPassword() {
        ApiUser user = new ApiUser(
                UUID.randomUUID(),
                "walkin@example.test",
                passwordEncoder.encode(PASSWORD),
                ApiUserRole.SUBMITTER,
                true,
                NOW
        );
        when(users.findByEmailIgnoreCase("walkin@example.test")).thenReturn(Optional.of(user));
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt());

        var response = authentication.login(new LoginRequest("walkin@example.test", PASSWORD));

        assertTrue(response.mustChangePassword());
    }

    @Test
    void createAssistedSubmitterCreatesAccountWithOneTimeTemporaryPassword() {
        when(users.existsByEmailIgnoreCase("walkin@example.test")).thenReturn(false);
        when(users.saveAndFlush(any(ApiUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var account = authentication.createAssistedSubmitter(" Walkin@Example.Test ");

        assertEquals("walkin@example.test", account.email());
        assertTrue(account.created());
        assertNotNull(account.temporaryPassword());
        assertTrue(account.temporaryPassword().length() >= 12);
        verify(users).saveAndFlush(argThat(user ->
                user.getRole() == ApiUserRole.SUBMITTER
                        && user.isMustChangePassword()
                        && passwordEncoder.matches(account.temporaryPassword(), user.getPasswordHash())
        ));
    }

    @Test
    void createAssistedSubmitterReusesExistingAccountWithoutExposingAPassword() {
        when(users.existsByEmailIgnoreCase("walkin@example.test")).thenReturn(true);

        var account = authentication.createAssistedSubmitter("walkin@example.test");

        assertEquals("walkin@example.test", account.email());
        assertFalse(account.created());
        assertNull(account.temporaryPassword());
    }

    @Test
    void changePasswordReplacesHashAndClearsTemporaryPasswordFlag() {
        ApiUser user = new ApiUser(
                UUID.randomUUID(),
                "walkin@example.test",
                passwordEncoder.encode(PASSWORD),
                ApiUserRole.SUBMITTER,
                true,
                NOW
        );
        when(users.findByEmailIgnoreCase("walkin@example.test")).thenReturn(Optional.of(user));

        authentication.changePassword(
                "walkin@example.test",
                new ChangePasswordRequest(PASSWORD, "a brand new password")
        );

        assertFalse(user.isMustChangePassword());
        assertTrue(passwordEncoder.matches("a brand new password", user.getPasswordHash()));
        verify(users).save(user);
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        ApiUser user = user("walkin@example.test", ApiUserRole.SUBMITTER);
        when(users.findByEmailIgnoreCase("walkin@example.test")).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authentication.changePassword(
                        "walkin@example.test",
                        new ChangePasswordRequest("wrong password", "a brand new password")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void changePasswordRejectsReusingTheCurrentPassword() {
        ApiUser user = user("walkin@example.test", ApiUserRole.SUBMITTER);
        when(users.findByEmailIgnoreCase("walkin@example.test")).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authentication.changePassword(
                        "walkin@example.test",
                        new ChangePasswordRequest(PASSWORD, PASSWORD)
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .claim("sub", "walkin@example.test")
                .issuedAt(NOW)
                .expiresAt(NOW.plus(Duration.ofMinutes(15)))
                .build();
    }

    private ApiUser user(String email, ApiUserRole role) {
        return new ApiUser(UUID.randomUUID(), email, passwordEncoder.encode(PASSWORD), role, NOW);
    }
}
