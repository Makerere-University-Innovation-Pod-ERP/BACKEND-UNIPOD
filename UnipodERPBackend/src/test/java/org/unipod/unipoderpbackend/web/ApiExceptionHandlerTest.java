package org.unipod.unipoderpbackend.web;

import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.submissions.InvalidSubmissionException;
import org.unipod.unipoderpbackend.submissions.SectorConflictException;
import org.unipod.unipoderpbackend.submissions.StorageObjectNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

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
        assertEquals("Bearer", response.getHeaders().getFirst("WWW-Authenticate"));
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

    @Test
    void methodNotAllowedPreservesAllowHeader() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("TRACE", List.of("GET", "POST"));

        ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupported(exception);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals("GET, POST", response.getHeaders().getFirst("Allow"));
    }

    @Test
    void sectorConflictReturnsSafeConflictEnvelope() {
        ResponseEntity<ProblemDetail> response = handler.handleSectorConflict(new SectorConflictException());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Sector conflict", response.getBody().getTitle());
        assertEquals(
                "The sector was changed by another request or conflicts with an existing name.",
                response.getBody().getDetail()
        );
    }

    @Test
    void missingDocumentReturnsSafeNotFoundEnvelope() {
        ResponseEntity<ProblemDetail> response = handler.handleDocumentNotFound(new StorageObjectNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Document not found", response.getBody().getTitle());
        assertEquals("The requested document was not found.", response.getBody().getDetail());
    }
}
