package org.unipod.unipoderpbackend.config;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenConfigurationTest {
    @Test
    void firstPartyTokensAreSignedAndValidatedForIssuerAndAudience() {
        JwtTokenConfiguration configuration = new JwtTokenConfiguration();
        byte[] secret = new byte[32];
        java.util.Arrays.fill(secret, (byte) 7);
        var environment = new StandardEnvironment();
        var key = configuration.jwtSecretKey(environment, Base64.getEncoder().encodeToString(secret));
        var encoder = configuration.jwtEncoder(key);
        var decoder = configuration.jwtDecoder(key, environment, "https://api.example.test", "unipod-erp-api");
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(900);

        String token = encoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("https://api.example.test")
                        .subject("admin@example.test")
                        .audience(List.of("unipod-erp-api"))
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .claim("roles", List.of("ADMINISTRATOR"))
                        .build()
        )).getTokenValue();

        var decoded = decoder.decode(token);

        assertEquals("admin@example.test", decoded.getSubject());
        assertEquals(List.of("ADMINISTRATOR"), decoded.getClaimAsStringList("roles"));
        assertEquals(List.of("unipod-erp-api"), decoded.getAudience());
    }
}
