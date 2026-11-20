package org.unipod.unipoderpbackend.service.auth;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorAttachment;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.auth.LoginResponse;
import org.unipod.unipoderpbackend.dto.auth.PasskeyChallengeResponse;
import org.unipod.unipoderpbackend.dto.auth.PasskeyFinishRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.PasskeyChallenge;
import org.unipod.unipoderpbackend.model.auth.PasskeyCredential;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.auth.PasskeyChallengeRepository;
import org.unipod.unipoderpbackend.repository.auth.PasskeyCredentialRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class PasskeyService {
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final String REGISTRATION = "REGISTRATION";
    private static final String AUTHENTICATION = "AUTHENTICATION";

    private final RelyingParty relyingParty;
    private final ApiUserRepository users;
    private final PasskeyCredentialRepository credentials;
    private final PasskeyChallengeRepository challenges;
    private final SessionService sessions;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public PasskeyService(
            RelyingParty relyingParty,
            ApiUserRepository users,
            PasskeyCredentialRepository credentials,
            PasskeyChallengeRepository challenges,
            SessionService sessions,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.relyingParty = relyingParty;
        this.users = users;
        this.credentials = credentials;
        this.challenges = challenges;
        this.sessions = sessions;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PasskeyChallengeResponse startRegistration(ApiUser user) {
        Instant now = clock.instant();
        challenges.deleteExpired(now);
        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(UserIdentity.builder()
                                .name(user.getEmail())
                                .displayName(user.getEmail())
                                .id(userHandle(user.getId()))
                                .build())
                        .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                                .residentKey(ResidentKeyRequirement.REQUIRED)
                                .userVerification(UserVerificationRequirement.REQUIRED)
                                .build())
                        .build()
        );
        try {
            return storeChallenge(
                    user.getId(),
                    REGISTRATION,
                    options.toJson(),
                    options.toCredentialsCreateJson(),
                    now
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize passkey registration options.", exception);
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void finishRegistration(ApiUser user, PasskeyFinishRequest request) {
        PasskeyChallenge challenge = consumeChallenge(request.challengeId(), REGISTRATION, user.getId());
        try {
            PublicKeyCredentialCreationOptions options =
                    PublicKeyCredentialCreationOptions.fromJson(challenge.getRequestJson());
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential =
                    PublicKeyCredential.parseRegistrationResponseJson(request.credential().toString());
            RegistrationResult result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(options)
                    .response(credential)
                    .build());
            if (!result.isDiscoverable().orElse(false)) {
                throw invalidRegistration();
            }
            credentials.save(new PasskeyCredential(
                    result.getKeyId().getId().getBase64Url(),
                    user.getId(),
                    result.getPublicKeyCose().getBytes(),
                    result.getSignatureCount(),
                    clock.instant()
            ));
        } catch (IOException | RegistrationFailedException | IllegalArgumentException exception) {
            throw invalidRegistration();
        }
    }

    @Transactional
    public PasskeyChallengeResponse startAuthentication() {
        Instant now = clock.instant();
        challenges.deleteExpired(now);
        AssertionRequest request = relyingParty.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build());
        try {
            return storeChallenge(
                    null,
                    AUTHENTICATION,
                    request.toJson(),
                    request.toCredentialsGetJson(),
                    now
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize passkey authentication options.", exception);
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse finishAuthentication(PasskeyFinishRequest request) {
        PasskeyChallenge challenge = consumeChallenge(request.challengeId(), AUTHENTICATION, null);
        try {
            AssertionRequest assertionRequest = AssertionRequest.fromJson(challenge.getRequestJson());
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credential =
                    PublicKeyCredential.parseAssertionResponseJson(request.credential().toString());
            AssertionResult result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(assertionRequest)
                    .response(credential)
                    .build());
            if (!result.isSuccess()) {
                throw invalidAuthentication();
            }
            ApiUser user = users.findByEmailIgnoreCase(result.getUsername())
                    .filter(ApiUser::isEnabled)
                    .orElseThrow(PasskeyService::invalidAuthentication);
            credentials.updateSignatureCount(
                    result.getCredential().getCredentialId().getBase64Url(),
                    result.getSignatureCount()
            );
            return sessions.issue(user);
        } catch (IOException | AssertionFailedException | IllegalArgumentException exception) {
            throw invalidAuthentication();
        }
    }

    private PasskeyChallengeResponse storeChallenge(
            UUID userId,
            String ceremony,
            String requestJson,
            String clientOptionsJson,
            Instant now
    ) {
        UUID id = UUID.randomUUID();
        challenges.save(new PasskeyChallenge(
                id,
                userId,
                ceremony,
                requestJson,
                now.plus(CHALLENGE_TTL),
                now
        ));
        return new PasskeyChallengeResponse(id, objectMapper.readTree(clientOptionsJson));
    }

    private PasskeyChallenge consumeChallenge(UUID id, String ceremony, UUID userId) {
        PasskeyChallenge challenge = challenges.findByIdForUpdate(id)
                .orElseThrow(PasskeyService::invalidChallenge);
        challenges.delete(challenge);
        if (!challenge.getExpiresAt().isAfter(clock.instant())
                || !challenge.getCeremony().equals(ceremony)
                || !java.util.Objects.equals(challenge.getUserId(), userId)) {
            throw invalidChallenge();
        }
        return challenge;
    }

    private static ByteArray userHandle(UUID userId) {
        return new ByteArray(ByteBuffer.allocate(16)
                .putLong(userId.getMostSignificantBits())
                .putLong(userId.getLeastSignificantBits())
                .array());
    }

    private static ResponseStatusException invalidChallenge() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "The passkey challenge is invalid or expired.");
    }

    private static ResponseStatusException invalidRegistration() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passkey registration could not be verified.");
    }

    private static ResponseStatusException invalidAuthentication() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Passkey sign-in could not be verified.");
    }
}
