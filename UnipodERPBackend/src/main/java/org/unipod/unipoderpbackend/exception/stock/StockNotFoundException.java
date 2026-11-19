package org.unipod.unipoderpbackend.exception.stock;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class StockNotFoundException extends ResponseStatusException {
    public StockNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Stock item not found.");
    }
}
