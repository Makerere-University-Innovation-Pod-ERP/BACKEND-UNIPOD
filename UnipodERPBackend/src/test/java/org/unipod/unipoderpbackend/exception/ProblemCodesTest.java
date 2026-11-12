package org.unipod.unipoderpbackend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProblemCodesTest {
    @Test
    void mapsStatusesToStableCodes() {
        assertEquals("INVALID_REQUEST", ProblemCodes.forStatus(HttpStatus.BAD_REQUEST));
        assertEquals("FORBIDDEN", ProblemCodes.forStatus(HttpStatus.FORBIDDEN));
        assertEquals("CONFLICT", ProblemCodes.forStatus(HttpStatus.CONFLICT));
        assertEquals("VALIDATION_FAILED", ProblemCodes.forStatus(HttpStatus.UNPROCESSABLE_CONTENT));
        assertEquals("INTERNAL_ERROR", ProblemCodes.forStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals("REQUEST_FAILED", ProblemCodes.forStatus(HttpStatus.TOO_MANY_REQUESTS));
    }
}
