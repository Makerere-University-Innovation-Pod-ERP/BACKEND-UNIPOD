package org.unipod.unipoderpbackend.exception.equipment;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EquipmentNotFoundException extends ResponseStatusException {
    private EquipmentNotFoundException(String reason) {
        super(HttpStatus.NOT_FOUND, reason);
    }

    public static EquipmentNotFoundException equipment() {
        return new EquipmentNotFoundException("Equipment not found.");
    }

    public static EquipmentNotFoundException machine() {
        return new EquipmentNotFoundException("Machine not found.");
    }

    public static EquipmentNotFoundException loan() {
        return new EquipmentNotFoundException("Loan not found.");
    }
}
