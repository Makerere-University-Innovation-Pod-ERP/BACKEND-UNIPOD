package org.unipod.unipoderpbackend.config;

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtTokenConfiguration {
    @Bean
    SecretKey jwtSecretKey(
            Environment environment,
            @Value("${jwt.secret:}") String encodedSecret
    ) {
        if (encodedSecret.isBlank()) {
            if (isHosted(environment)) {
                throw new IllegalStateException("JWT_SECRET must be configured on the hosting platform.");
            }
            byte[] generatedSecret = new byte[32];
            new SecureRandom().nextBytes(generatedSecret);
            return new SecretKeySpec(generatedSecret, "HmacSHA256");
        }

        final byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64.", exception);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes.");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            Environment environment,
            @Value("${jwt.issuer-uri}") String issuer,
            @Value("${jwt.audience}") String audience
    ) {
        if (isHosted(environment)
                && (environment.getProperty("JWT_ISSUER_URI") == null
                || environment.getProperty("JWT_AUDIENCE") == null
                || environment.getProperty("JWT_ISSUER_URI").isBlank()
                || environment.getProperty("JWT_AUDIENCE").isBlank())) {
            throw new IllegalStateException("JWT_ISSUER_URI and JWT_AUDIENCE must be configured on Railway.");
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Required audience is missing.", null)
                        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator
        ));
        return decoder;
    }

    private static boolean isHosted(Environment environment) {
        return environment.getProperty("RAILWAY_ENVIRONMENT_ID") != null
                || environment.getProperty("RENDER_SERVICE_ID") != null;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
