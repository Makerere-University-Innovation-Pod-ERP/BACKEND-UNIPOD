package org.unipod.unipoderpbackend.exception.booking;

import java.util.UUID;

public class FacilityNotFoundException extends RuntimeException {
    public FacilityNotFoundException(UUID id) {
        super("Facility " + id + " was not found.");
    }
}
