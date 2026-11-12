package org.unipod.unipoderpbackend.exception.training;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TrainingNotFoundException extends ResponseStatusException {
    public TrainingNotFoundException(String reason) {
        super(HttpStatus.NOT_FOUND, reason);
    }

    public static TrainingNotFoundException session() {
        return new TrainingNotFoundException("Training session not found.");
    }
}
