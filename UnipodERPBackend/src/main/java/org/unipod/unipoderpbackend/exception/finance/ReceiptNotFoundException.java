package org.unipod.unipoderpbackend.exception.finance;

import java.util.UUID;

public class ReceiptNotFoundException extends RuntimeException {
    public ReceiptNotFoundException(UUID invoiceId) {
        super("No receipt has been issued for invoice " + invoiceId + ".");
    }
}
