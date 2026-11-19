package org.unipod.unipoderpbackend.exception.stock;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A stock rule the request body cannot express on its own (422). */
public class InvalidStockException extends ResponseStatusException {
    public InvalidStockException(String reason) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, reason);
    }
}
