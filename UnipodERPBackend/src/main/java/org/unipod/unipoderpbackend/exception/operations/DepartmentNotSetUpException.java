package org.unipod.unipoderpbackend.exception.operations;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The department's sector or facility has not been created yet, so the request cannot be placed (409). */
public class DepartmentNotSetUpException extends ResponseStatusException {
    public DepartmentNotSetUpException(String reason) {
        super(HttpStatus.CONFLICT, reason);
    }
}
