package org.unipod.unipoderpbackend.config;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.PublicKeyCredentialParameters;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unipod.unipoderpbackend.repository.auth.WebAuthnCredentialRepository;

@Configuration
public class WebAuthnConfiguration {
    @Bean
    RelyingParty relyingParty(
            WebAuthnCredentialRepository credentials,
            @Value("${webauthn.rp-id:localhost}") String rpId,
            @Value("${webauthn.origins:http://localhost:5173,http://localhost:3000}") String configuredOrigins
    ) {
        Set<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("At least one WebAuthn origin must be configured.");
        }
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(rpId.trim())
                        .name("MUTIC ERP")
                        .build())
                .credentialRepository(credentials)
                .origins(origins)
                .preferredPubkeyParams(List.of(
                        PublicKeyCredentialParameters.ES256,
                        PublicKeyCredentialParameters.RS256
                ))
                .build();
    }
}
