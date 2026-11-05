package org.unipod.unipoderpbackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler exceptionHandler = new ApiExceptionHandler();

    @Test
    void unexpectedExceptionReturnsGenericSafeEnvelope() {
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleException(
                new IllegalStateException("database password and internal location"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal Server Error", response.getBody().error());
        assertEquals("An unexpected error occurred.", response.getBody().message());
        assertFalse(response.getBody().toString().contains("database password"));
        assertFalse(response.getBody().toString().contains("internal location"));
    }

    @Test
    void clientErrorPreservesStatusWithoutExposingExceptionDetails() {
        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleException(
                new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "private resource details"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not Found", response.getBody().error());
        assertEquals("The requested resource was not found.", response.getBody().message());
        assertFalse(response.getBody().toString().contains("private resource details"));
    }
}
