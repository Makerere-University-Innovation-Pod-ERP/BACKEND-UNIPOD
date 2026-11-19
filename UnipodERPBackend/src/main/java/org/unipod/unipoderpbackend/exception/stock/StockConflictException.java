package org.unipod.unipoderpbackend.exception.stock;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class StockConflictException extends ResponseStatusException {
    public StockConflictException(String reason) {
        super(HttpStatus.CONFLICT, reason);
    }
}
