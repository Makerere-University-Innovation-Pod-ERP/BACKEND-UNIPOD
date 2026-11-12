package org.unipod.unipoderpbackend.exception.training;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TrainingConflictException extends ResponseStatusException {
    public TrainingConflictException(String reason) {
        super(HttpStatus.CONFLICT, reason);
    }
}
