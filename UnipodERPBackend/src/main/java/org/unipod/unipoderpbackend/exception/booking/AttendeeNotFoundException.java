package org.unipod.unipoderpbackend.exception.booking;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AttendeeNotFoundException extends ResponseStatusException {
    public AttendeeNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Participant not found.");
    }
}
