package org.unipod.unipoderpbackend.exception.partner;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PartnerConflictException extends ResponseStatusException {
    public PartnerConflictException(String reason) {
        super(HttpStatus.CONFLICT, reason);
    }
}
