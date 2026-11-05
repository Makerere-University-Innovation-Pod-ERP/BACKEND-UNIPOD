package org.unipod.unipoderpbackend.web;

import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.submissions.InvalidSubmissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unexpectedFailuresReturnGenericEnvelopeWithoutExceptionDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/submissions");
        request.addHeader("X-Request-Id", "test-reference-123");

        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
                new IllegalStateException("relation submissions does not exist"),
                request
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertFalse(problem.getDetail().contains("relation submissions"));
        assertFalse(problem.getDetail().contains("IllegalStateException"));
        assertTrue(problem.getDetail().contains("test-reference-123"));
        assertEquals("test-reference-123", problem.getProperties().get("reference"));
    }

    @Test
    void accessDeniedReturnsForbiddenEnvelope() {
        ResponseEntity<ProblemDetail> response = handler.handleAccessDenied(
                new AccessDeniedException("internal rule")
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Forbidden", response.getBody().getTitle());
        assertFalse(response.getBody().getDetail().contains("internal rule"));
    }

    @Test
    void submissionErrorsDoNotExposeExceptionMessages() {
        ResponseEntity<ProblemDetail> response = handler.handleInvalidSubmission(
                new InvalidSubmissionException("SQL details and internal implementation path")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Submission details are invalid or incomplete.", response.getBody().getDetail());
        assertFalse(response.getBody().getDetail().contains("SQL details"));
        assertFalse(response.getBody().getDetail().contains("implementation path"));
    }
}
