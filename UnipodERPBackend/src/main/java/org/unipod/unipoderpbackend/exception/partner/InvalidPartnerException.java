package org.unipod.unipoderpbackend.exception.partner;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidPartnerException extends ResponseStatusException {
    public InvalidPartnerException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}
