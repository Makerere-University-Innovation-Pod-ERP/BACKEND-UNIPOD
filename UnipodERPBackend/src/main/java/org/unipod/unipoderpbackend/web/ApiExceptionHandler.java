package org.unipod.unipoderpbackend.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.unipod.unipoderpbackend.submissions.InvalidSubmissionException;
import org.unipod.unipoderpbackend.submissions.SubmissionConflictException;
import org.unipod.unipoderpbackend.submissions.SubmissionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Translates every exception into a consistent RFC 9457 problem envelope.
 *
 * <p>Internal details (stack traces, exception types, SQL, class names) are never
 * written to the response. Unexpected failures return a generic message plus a
 * reference that operators can use to find the full stack trace in the server
 * logs.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @ExceptionHandler(InvalidSubmissionException.class)
    ResponseEntity<ProblemDetail> handleInvalidSubmission(InvalidSubmissionException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid submission",
                "Submission details are invalid or incomplete."
        );
    }

    @ExceptionHandler(SubmissionConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(SubmissionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Submission conflict",
                "The submission cannot be changed in its current state."
        );
    }

    @ExceptionHandler(SubmissionNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(SubmissionNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Submission not found",
                "The requested submission was not found."
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more request values are invalid.");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more request values are invalid.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more request values are invalid.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "A request parameter has an invalid value.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "A required request parameter is missing."
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request body",
                "The request body is missing or is not valid JSON. Check the JSON syntax and that enum values match the API contract."
        );
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ProblemDetail> handleResourceNotFound(Exception exception) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "The requested resource was not found.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        ProblemDetail problem = problemDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed",
                "The HTTP method is not supported for this resource."
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(exception.getHeaders())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        return problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type",
                "The request Content-Type is not supported. Use application/json."
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ProblemDetail> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return problem(
                HttpStatus.NOT_ACCEPTABLE,
                "Not acceptable",
                "The requested response format is not supported."
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
        ProblemDetail problem = problemDetail(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "You do not have permission to perform this action."
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("WWW-Authenticate", "Bearer")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        String reference = reference(request);
        log.error(
                "Unhandled exception [reference={}] for {} {}",
                reference,
                request.getMethod(),
                request.getRequestURI(),
                exception
        );
        ProblemDetail problem = problemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "The server could not complete the request. Quote reference %s when contacting support."
                        .formatted(reference)
        );
        problem.setProperty("reference", reference);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, problem);
    }

    private static String reference(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        if (header != null && SAFE_REFERENCE.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        return response(status, problemDetail(status, title, detail));
    }

    private static ProblemDetail problemDetail(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> response(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
