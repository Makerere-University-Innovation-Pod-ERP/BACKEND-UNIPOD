package org.unipod.unipoderpbackend.submissions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class SubmissionExceptionHandler {
    @ExceptionHandler(InvalidSubmissionException.class)
    ResponseEntity<ProblemDetail> handleInvalidSubmission(InvalidSubmissionException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid submission", exception.getMessage());
    }

    @ExceptionHandler(SubmissionConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(SubmissionConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Submission conflict", exception.getMessage());
    }

    @ExceptionHandler(SubmissionNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(SubmissionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Submission not found", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .distinct()
                .reduce((first, second) -> first + "; " + second)
                .orElse("The request is invalid.");
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "A request parameter has an invalid value.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }
}
