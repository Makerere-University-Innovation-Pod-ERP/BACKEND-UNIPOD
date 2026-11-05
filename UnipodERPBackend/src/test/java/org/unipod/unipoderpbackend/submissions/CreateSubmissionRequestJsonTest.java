package org.unipod.unipoderpbackend.submissions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;

@JsonTest
class CreateSubmissionRequestJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rejectsUndeclaredRequestProperties() {
        String request = """
                {
                  "category": "general",
                  "title": "Test",
                  "purpose": "Test purpose",
                  "problemAddressed": "Test problem",
                  "internalDebug": "must not be silently accepted"
                }
                """;

        assertThrows(
                JacksonException.class,
                () -> objectMapper.readValue(request, CreateSubmissionRequest.class)
        );
    }
}
