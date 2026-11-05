package org.unipod.unipoderpbackend;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public record ApiErrorResponse(Instant timestamp, int status, String error, String message) {

    public static ApiErrorResponse from(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String error = status == null ? "Error" : status.getReasonPhrase();

        return new ApiErrorResponse(
                Instant.now(),
                statusCode.value(),
                error,
                messageFor(statusCode.value()));
    }

    private static String messageFor(int status) {
        return switch (status) {
            case 400 -> "The request is invalid.";
            case 401 -> "Authentication is required.";
            case 403 -> "You are not allowed to perform this action.";
            case 404 -> "The requested resource was not found.";
            case 405 -> "The HTTP method is not supported for this resource.";
            case 409 -> "The request conflicts with the current resource state.";
            case 415 -> "The request content type is not supported.";
            case 422 -> "Request validation failed.";
            case 429 -> "Too many requests.";
            default -> status >= 500
                    ? "An unexpected error occurred."
                    : "The request could not be processed.";
        };
    }
}
