package org.unipod.unipoderpbackend.exception.finance;

import java.util.UUID;

public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(UUID id) {
        super("Invoice " + id + " was not found.");
    }
}
