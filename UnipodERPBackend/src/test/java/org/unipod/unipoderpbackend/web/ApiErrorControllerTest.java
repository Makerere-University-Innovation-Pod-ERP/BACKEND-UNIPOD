package org.unipod.unipoderpbackend.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiErrorControllerTest {

    private final ApiErrorController errorController = new ApiErrorController();

    @Test
    void unauthorizedErrorDispatchIncludesBearerChallenge() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.UNAUTHORIZED.value());

        var response = errorController.error(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Bearer", response.getHeaders().getFirst("WWW-Authenticate"));
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    }

    @Test
    void forbiddenErrorDispatchIncludesBearerChallenge() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.FORBIDDEN.value());

        var response = errorController.error(request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Bearer", response.getHeaders().getFirst("WWW-Authenticate"));
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    }

    @Test
    void unexpectedContainerErrorUsesSafeReferenceAndOmitsFailureDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.INTERNAL_SERVER_ERROR.value());
        request.setAttribute(
                RequestDispatcher.ERROR_EXCEPTION,
                new IllegalStateException("database password and stack trace")
        );
        request.addHeader("X-Request-Id", "safe-reference-123");

        var response = errorController.error(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        assertEquals("safe-reference-123", response.getBody().getProperties().get("reference"));
        assertTrue(response.getBody().getDetail().contains("safe-reference-123"));
        assertFalse(response.getBody().getDetail().contains("database password"));
        assertFalse(response.getBody().getDetail().contains("IllegalStateException"));
    }

    @Test
    void invalidRequestIdIsReplacedBeforeItReachesTheErrorEnvelope() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.INTERNAL_SERVER_ERROR.value());
        request.addHeader("X-Request-Id", "attacker supplied value with spaces");

        var response = errorController.error(request);

        String reference = (String) response.getBody().getProperties().get("reference");
        assertTrue(reference.matches("[A-Za-z0-9-]{1,64}"));
        assertFalse(response.getBody().getDetail().contains("attacker supplied"));
    }
}
