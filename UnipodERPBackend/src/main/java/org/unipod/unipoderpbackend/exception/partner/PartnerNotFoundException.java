package org.unipod.unipoderpbackend.exception.partner;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class PartnerNotFoundException extends ResponseStatusException {
    public PartnerNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Partner not found.");
    }
}
