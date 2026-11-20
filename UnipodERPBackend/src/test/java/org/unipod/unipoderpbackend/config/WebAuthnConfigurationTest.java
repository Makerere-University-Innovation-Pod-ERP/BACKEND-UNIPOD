package org.unipod.unipoderpbackend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.repository.auth.WebAuthnCredentialRepository;

class WebAuthnConfigurationTest {
    private final WebAuthnConfiguration configuration = new WebAuthnConfiguration();
    private final WebAuthnCredentialRepository credentials = mock(WebAuthnCredentialRepository.class);

    @Test
    void acceptsValidOriginsAndHostnameRpId() {
        assertDoesNotThrow(() -> configuration.relyingParty(
                credentials,
                "localhost",
                "http://localhost:5173,http://localhost:3000"
        ));
        assertDoesNotThrow(() -> configuration.relyingParty(
                credentials,
                "example.test",
                "https://example.test,https://portal.example.test"
        ));
    }

    @Test
    void rejectsOriginsThatAreNotBareHttpOrigins() {
        for (String origin : new String[] {
                "https://frontend.example.test/",
                "https://frontend.example.test/app",
                "frontend.example.test",
                "https://frontend.example.test?next=/"
        }) {
            assertThrows(IllegalArgumentException.class, () -> configuration.relyingParty(
                    credentials,
                    "frontend.example.test",
                    origin
            ));
        }
    }

    @Test
    void rejectsRpIdsThatAreNotHostnames() {
        for (String rpId : new String[] {
                "",
                "https://frontend.example.test",
                "frontend.example.test:443",
                "frontend.example.test/path"
        }) {
            assertThrows(IllegalArgumentException.class, () -> configuration.relyingParty(
                    credentials,
                    rpId,
                    "https://frontend.example.test"
            ));
        }
    }

    @Test
    void rejectsOriginsOutsideTheRpIdDomain() {
        assertThrows(IllegalArgumentException.class, () -> configuration.relyingParty(
                credentials,
                "example.test",
                "https://notexample.test"
        ));
    }
}
