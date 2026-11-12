package org.unipod.unipoderpbackend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.unipod.unipoderpbackend.dto.FieldErrorResponse;
import org.unipod.unipoderpbackend.exception.auth.InvalidSignupException;
import org.unipod.unipoderpbackend.exception.authorization.AccessDeniedWithReasonException;
import org.unipod.unipoderpbackend.exception.notification.NotificationNotFoundException;
import org.unipod.unipoderpbackend.exception.sector.SectorConflictException;
import org.unipod.unipoderpbackend.exception.sector.SectorNotFoundException;
import org.unipod.unipoderpbackend.exception.storage.StorageObjectNotFoundException;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;

/**
 * Translates every exception into a consistent RFC 9457 problem envelope.
 *
 * <p>Internal details (stack traces, exception types, SQL, class names) are never
 * written to the response. Unexpected failures return a generic message plus a
 * reference that operators can use to find the full stack trace in the server
 * logs.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @ExceptionHandler(InvalidSubmissionException.class)
    ResponseEntity<ProblemDetail> handleInvalidSubmission(InvalidSubmissionException exception) {
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Invalid submission",
                "Submission details are invalid or incomplete.",
                "SUBMISSION_INVALID"
        );
    }

    @ExceptionHandler(SubmissionConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(SubmissionConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Submission conflict",
                exception.getMessage(),
                "SUBMISSION_CONFLICT"
        );
    }

    @ExceptionHandler(SectorConflictException.class)
    ResponseEntity<ProblemDetail> handleSectorConflict(SectorConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Sector conflict", "The sector was changed by another request or conflicts with an existing name.", "SECTOR_CONFLICT");
    }

    @ExceptionHandler(SubmissionNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(SubmissionNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Submission not found",
                "The requested submission was not found.",
                "SUBMISSION_NOT_FOUND"
        );
    }

    @ExceptionHandler(SectorNotFoundException.class)
    ResponseEntity<ProblemDetail> handleSectorNotFound(SectorNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Sector not found", "The requested sector was not found or is inactive.", "SECTOR_NOT_FOUND");
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotificationNotFound(NotificationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Notification not found", "The requested notification was not found.", "NOTIFICATION_NOT_FOUND");
    }

    @ExceptionHandler(StorageObjectNotFoundException.class)
    ResponseEntity<ProblemDetail> handleDocumentNotFound(StorageObjectNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Document not found", "The requested document was not found.", "DOCUMENT_NOT_FOUND");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large", "The uploaded file exceeds the allowed size.");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "Edit conflict", "Someone else changed this record at the same time. Reload it and try again.", "EDIT_CONFLICT");
    }

    @ExceptionHandler(InvalidSignupException.class)
    ResponseEntity<ProblemDetail> handleInvalidSignup(InvalidSignupException exception) {
        return validationProblem(exception.getErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException exception) {
        List<FieldErrorResponse> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorResponse(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage()
                        )))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldErrorResponse> errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(lastNode(violation), violation.getMessage()))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "A request parameter has an invalid value.", "INVALID_PARAMETER");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "A required request parameter is missing.",
                "MISSING_PARAMETER"
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request body",
                "The request body is missing or is not valid JSON. Check the JSON syntax and that enum values match the API contract.",
                "MALFORMED_BODY"
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

    @ExceptionHandler(AccessDeniedWithReasonException.class)
    ResponseEntity<ProblemDetail> handleAccessDeniedWithReason(AccessDeniedWithReasonException exception) {
        ProblemDetail problem = problemDetail(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                exception.getDecision().reason()
        );
        problem.setProperty("code", exception.getDecision().code().name());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("WWW-Authenticate", "Bearer")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
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

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String detail = exception.getReason();
        return problem(status, status.getReasonPhrase(), detail == null ? status.getReasonPhrase() : detail);
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

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = problemDetail(status, title, detail);
        problem.setProperty("code", code);
        return response(status, problem);
    }

    private static ResponseEntity<ProblemDetail> validationProblem(List<FieldErrorResponse> errors) {
        ProblemDetail problem = problemDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Validation failed",
                "One or more request values are invalid."
        );
        problem.setProperty("errors", errors);
        return response(HttpStatus.UNPROCESSABLE_CONTENT, problem);
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String field = null;
        for (Path.Node node : violation.getPropertyPath()) {
            field = node.getName();
        }
        return field;
    }

    private static ProblemDetail problemDetail(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", ProblemCodes.forStatus(status));
        return problem;
    }

    private static ResponseEntity<ProblemDetail> response(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
