package org.unipod.unipoderpbackend.exception;

import org.springframework.http.HttpStatusCode;

public final class ProblemCodes {
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    private ProblemCodes() {
    }

    public static String forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> VALIDATION_FAILED;
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        };
    }
}
