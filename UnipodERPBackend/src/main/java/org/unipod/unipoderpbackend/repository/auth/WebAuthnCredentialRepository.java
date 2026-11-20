package org.unipod.unipoderpbackend.repository.auth;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.exception.Base64UrlException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.PasskeyCredential;

@Repository
public class WebAuthnCredentialRepository implements CredentialRepository {
    private final PasskeyCredentialRepository credentials;
    private final ApiUserRepository users;

    public WebAuthnCredentialRepository(PasskeyCredentialRepository credentials, ApiUserRepository users) {
        this.credentials = credentials;
        this.users = users;
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return users.findByEmailIgnoreCase(username)
                .filter(ApiUser::isEnabled)
                .map(user -> credentials.findAllByUserId(user.getId()).stream()
                        .map(credential -> PublicKeyCredentialDescriptor.builder()
                                .id(credentialId(credential.getCredentialId()))
                                .build())
                        .collect(Collectors.toUnmodifiableSet()))
                .orElseGet(Set::of);
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return users.findByEmailIgnoreCase(username)
                .filter(ApiUser::isEnabled)
                .map(ApiUser::getId)
                .map(WebAuthnCredentialRepository::userHandle);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return userId(userHandle)
                .flatMap(users::findById)
                .filter(ApiUser::isEnabled)
                .map(ApiUser::getEmail);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        Optional<UUID> userId = userId(userHandle);
        if (userId.isEmpty()) {
            return Optional.empty();
        }
        return credentials.findById(credentialId.getBase64Url())
                .filter(credential -> credential.getUserId().equals(userId.get()))
                .map(credential -> toRegisteredCredential(credential, userHandle));
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credentials.findById(credentialId.getBase64Url())
                .map(credential -> Set.of(toRegisteredCredential(
                        credential,
                        userHandle(credential.getUserId())
                )))
                .orElseGet(Set::of);
    }

    private static RegisteredCredential toRegisteredCredential(
            PasskeyCredential credential,
            ByteArray userHandle
    ) {
        return RegisteredCredential.builder()
                .credentialId(credentialId(credential.getCredentialId()))
                .userHandle(userHandle)
                .publicKeyCose(new ByteArray(credential.getPublicKeyCose()))
                .signatureCount(credential.getSignatureCount())
                .build();
    }

    private static ByteArray credentialId(String encodedId) {
        try {
            return ByteArray.fromBase64Url(encodedId);
        } catch (Base64UrlException exception) {
            throw new IllegalStateException("A stored passkey credential ID is malformed.", exception);
        }
    }

    private static ByteArray userHandle(UUID userId) {
        return new ByteArray(ByteBuffer.allocate(16)
                .putLong(userId.getMostSignificantBits())
                .putLong(userId.getLeastSignificantBits())
                .array());
    }

    private static Optional<UUID> userId(ByteArray userHandle) {
        byte[] bytes = userHandle.getBytes();
        if (bytes.length != 16) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }
}
