package org.unipod.unipoderpbackend.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpsUrlValidatorTest {
    private final HttpsUrlValidator validator = new HttpsUrlValidator();

    @Test
    void allowsBlankValuesSoTheLinkCanBeCleared() {
        assertTrue(validator.isValid(null, null));
        assertTrue(validator.isValid("", null));
        assertTrue(validator.isValid("   ", null));
    }

    @Test
    void acceptsAnAbsoluteHttpsUrlWithAHost() {
        assertTrue(validator.isValid("https://chat.whatsapp.com/abc123", null));
        assertTrue(validator.isValid("  https://example.test:8443/invite  ", null));
    }

    @Test
    void rejectsPrefixesOtherSchemesAndMissingHosts() {
        assertFalse(validator.isValid("chat.whatsapp.com/abc123", null));
        assertFalse(validator.isValid("http://chat.whatsapp.com/abc123", null));
        assertFalse(validator.isValid("https://", null));
        assertFalse(validator.isValid("https:///invite", null));
        assertFalse(validator.isValid("https://exa mple.test/invite", null));
        assertFalse(validator.isValid("https://user@", null));
    }
}
