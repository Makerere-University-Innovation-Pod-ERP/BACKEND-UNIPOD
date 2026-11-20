package org.unipod.unipoderpbackend.config;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.PublicKeyCredentialParameters;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.unipod.unipoderpbackend.repository.auth.WebAuthnCredentialRepository;

@Configuration
public class WebAuthnConfiguration {
    @Bean
    RelyingParty relyingParty(
            WebAuthnCredentialRepository credentials,
            @Value("${webauthn.rp-id}") String rpId,
            @Value("${webauthn.origins}") String configuredOrigins
    ) {
        String validatedRpId = validateRpId(rpId);
        Set<String> origins = parseOrigins(configuredOrigins);
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(validatedRpId)
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

    private static String validateRpId(String rpId) {
        if (rpId == null || rpId.isBlank()) {
            throw new IllegalArgumentException("WEBAUTHN_RP_ID must be a hostname.");
        }
        String normalized = rpId.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("://") || normalized.contains(":")
                || normalized.contains("/") || normalized.contains("?")
                || normalized.contains("#") || normalized.contains("@")) {
            throw new IllegalArgumentException("WEBAUTHN_RP_ID must be a hostname without scheme, port, or path.");
        }
        String ascii;
        try {
            ascii = IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("WEBAUTHN_RP_ID must be a valid hostname.", exception);
        }
        if (ascii.length() > 253 || Arrays.stream(ascii.split("\\.", -1))
                .anyMatch(label -> label.isEmpty() || label.length() > 63
                        || label.startsWith("-") || label.endsWith("-"))) {
            throw new IllegalArgumentException("WEBAUTHN_RP_ID must be a valid hostname.");
        }
        return ascii;
    }

    private static Set<String> parseOrigins(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            throw new IllegalArgumentException("WEBAUTHN_ORIGINS must contain at least one valid origin.");
        }
        Set<String> origins = Arrays.stream(configuredOrigins.split(",", -1))
                .map(String::trim)
                .map(WebAuthnConfiguration::normalizeOrigin)
                .collect(Collectors.toUnmodifiableSet());
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("WEBAUTHN_ORIGINS must contain at least one valid origin.");
        }
        return origins;
    }

    private static String normalizeOrigin(String origin) {
        URI uri;
        try {
            uri = new URI(origin);
        } catch (URISyntaxException exception) {
            throw invalidOrigin(origin, exception);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        if (scheme == null || host == null || uri.getRawUserInfo() != null
                || uri.getRawPath() != null && !uri.getRawPath().isEmpty()
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || port == 0 || port > 65535) {
            throw invalidOrigin(origin, null);
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean defaultPort = port == 80 && normalizedScheme.equals("http")
                || port == 443 && normalizedScheme.equals("https");
        return normalizedScheme + "://" + normalizedHost
                + (port < 0 || defaultPort ? "" : ":" + port);
    }

    private static IllegalArgumentException invalidOrigin(String origin, Exception cause) {
        String message = "Each WEBAUTHN_ORIGINS entry must be an origin with scheme and host only: " + origin;
        return cause == null
                ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }
}
