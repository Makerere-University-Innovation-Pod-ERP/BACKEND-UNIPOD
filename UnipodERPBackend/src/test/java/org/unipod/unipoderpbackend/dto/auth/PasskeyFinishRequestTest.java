package org.unipod.unipoderpbackend.dto.auth;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PasskeyFinishRequestTest {
    @Test
    void normalizesExplicitJsonNullCredentialToNull() throws Exception {
        var nullCredential = JsonMapper.builder().build().readTree("null");

        PasskeyFinishRequest request = new PasskeyFinishRequest(UUID.randomUUID(), nullCredential);

        assertNull(request.credential());
        PasskeyRegistrationFinishRequest registrationRequest =
                new PasskeyRegistrationFinishRequest(UUID.randomUUID(), nullCredential, "password");
        assertNull(registrationRequest.credential());
    }
}
