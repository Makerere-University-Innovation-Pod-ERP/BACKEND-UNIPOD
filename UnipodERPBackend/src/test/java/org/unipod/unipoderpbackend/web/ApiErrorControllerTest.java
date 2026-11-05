package org.unipod.unipoderpbackend.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
