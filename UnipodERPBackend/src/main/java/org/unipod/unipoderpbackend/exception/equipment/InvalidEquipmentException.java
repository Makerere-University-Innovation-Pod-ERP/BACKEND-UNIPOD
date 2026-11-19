package org.unipod.unipoderpbackend.exception.equipment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A rule the request body cannot express on its own (422). */
public class InvalidEquipmentException extends ResponseStatusException {
    public InvalidEquipmentException(String reason) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, reason);
    }
}
