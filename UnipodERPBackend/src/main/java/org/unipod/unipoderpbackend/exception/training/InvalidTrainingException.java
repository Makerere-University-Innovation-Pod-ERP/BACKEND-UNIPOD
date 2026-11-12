package org.unipod.unipoderpbackend.exception.training;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidTrainingException extends ResponseStatusException {
    public InvalidTrainingException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}
