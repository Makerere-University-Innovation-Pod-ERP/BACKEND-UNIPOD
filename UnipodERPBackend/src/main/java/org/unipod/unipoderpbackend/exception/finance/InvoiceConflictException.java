package org.unipod.unipoderpbackend.exception.finance;

public class InvoiceConflictException extends RuntimeException {
    public InvoiceConflictException(String message) {
        super(message);
    }
}
