package org.unipod.unipoderpbackend.exception.equipment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The request is fine but the equipment, machine or loan is in a state that does not allow it (409). */
public class EquipmentConflictException extends ResponseStatusException {
    public EquipmentConflictException(String reason) {
        super(HttpStatus.CONFLICT, reason);
    }
}
