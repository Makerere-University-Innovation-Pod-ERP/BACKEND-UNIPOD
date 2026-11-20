package org.unipod.unipoderpbackend.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialParameters;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.auth.PasskeyChallenge;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.auth.PasskeyChallengeRepository;
import org.unipod.unipoderpbackend.repository.auth.PasskeyCredentialRepository;
import tools.jackson.databind.json.JsonMapper;

class PasskeyServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void createsUsernamelessAuthenticationOptionsAndPersistsExpiringChallenge() {
        PasskeyChallengeRepository challenges = mock(PasskeyChallengeRepository.class);
        PasskeyService service = service(challenges);

        var response = service.startAuthentication();

        ArgumentCaptor<PasskeyChallenge> saved = ArgumentCaptor.forClass(PasskeyChallenge.class);
        verify(challenges).save(saved.capture());
        assertEquals(response.challengeId(), saved.getValue().getId());
        assertEquals("AUTHENTICATION", saved.getValue().getCeremony());
        assertEquals(NOW.plusSeconds(300), saved.getValue().getExpiresAt());
        assertEquals("required", response.options().path("publicKey").path("userVerification").asText());
        assertNotNull(response.options().path("publicKey").path("challenge").asText());
        assertTrue(response.options().path("publicKey").path("allowCredentials").isEmpty());
    }

    @Test
    void createsPlatformDiscoverableRegistrationOptionsForTheSignedInUser() {
        PasskeyChallengeRepository challenges = mock(PasskeyChallengeRepository.class);
        PasskeyService service = service(challenges);
        ApiUser user = new ApiUser(
                UUID.randomUUID(),
                "person@example.test",
                "password-hash",
                ApiUserRole.SUBMITTER,
                NOW
        );

        var response = service.startRegistration(user);

        ArgumentCaptor<PasskeyChallenge> saved = ArgumentCaptor.forClass(PasskeyChallenge.class);
        verify(challenges).save(saved.capture());
        assertEquals(user.getId(), saved.getValue().getUserId());
        assertEquals("REGISTRATION", saved.getValue().getCeremony());
        assertEquals("platform", response.options().path("publicKey")
                .path("authenticatorSelection").path("authenticatorAttachment").asText());
        assertEquals("required", response.options().path("publicKey")
                .path("authenticatorSelection").path("residentKey").asText());
        assertEquals("required", response.options().path("publicKey")
                .path("authenticatorSelection").path("userVerification").asText());
    }

    private static PasskeyService service(PasskeyChallengeRepository challenges) {
        RelyingParty relyingParty = RelyingParty.builder()
                .identity(com.yubico.webauthn.data.RelyingPartyIdentity.builder()
                        .id("localhost")
                        .name("MUTIC ERP")
                        .build())
                .credentialRepository(new EmptyCredentialRepository())
                .origins(Set.of("http://localhost:5173"))
                .preferredPubkeyParams(List.of(
                        PublicKeyCredentialParameters.ES256,
                        PublicKeyCredentialParameters.RS256
                ))
                .build();
        return new PasskeyService(
                relyingParty,
                mock(ApiUserRepository.class),
                mock(PasskeyCredentialRepository.class),
                challenges,
                mock(SessionService.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                JsonMapper.builder().build()
        );
    }

    private static class EmptyCredentialRepository implements CredentialRepository {
        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            return Set.of();
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            return Optional.empty();
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            return Optional.empty();
        }

        @Override
        public Optional<com.yubico.webauthn.RegisteredCredential> lookup(
                ByteArray credentialId,
                ByteArray userHandle
        ) {
            return Optional.empty();
        }

        @Override
        public Set<com.yubico.webauthn.RegisteredCredential> lookupAll(ByteArray credentialId) {
            return Set.of();
        }
    }
}
