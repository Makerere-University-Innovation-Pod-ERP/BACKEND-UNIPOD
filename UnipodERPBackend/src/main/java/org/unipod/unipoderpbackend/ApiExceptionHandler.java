package org.unipod.unipoderpbackend;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        HttpStatusCode status = exception instanceof ErrorResponse errorResponse
                ? errorResponse.getStatusCode()
                : HttpStatusCode.valueOf(500);

        if (status.is5xxServerError()) {
            logger.error("Unhandled exception while processing request", exception);
        }

        return ResponseEntity.status(status).body(ApiErrorResponse.from(status));
    }
}
