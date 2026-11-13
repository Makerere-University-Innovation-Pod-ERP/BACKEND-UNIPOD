package org.unipod.unipoderpbackend.dto.finance;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.finance.InvoiceReceipt;

public record ReceiptResponse(
        UUID id,
        UUID invoiceId,
        String receiptNumber,
        String issuedBy,
        Instant issuedAt
) {
    public static ReceiptResponse from(InvoiceReceipt receipt) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getInvoiceId(),
                receipt.getReceiptNumber(),
                receipt.getIssuedBy(),
                receipt.getIssuedAt()
        );
    }
}
