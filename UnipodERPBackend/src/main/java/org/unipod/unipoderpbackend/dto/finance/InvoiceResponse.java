package org.unipod.unipoderpbackend.dto.finance;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.finance.Invoice;
import org.unipod.unipoderpbackend.model.finance.InvoiceSourceType;
import org.unipod.unipoderpbackend.model.finance.InvoiceStatus;

public record InvoiceResponse(
        UUID id,
        String number,
        String customerEmail,
        String customerName,
        String department,
        String description,
        long amount,
        String currency,
        InvoiceSourceType sourceType,
        String sourceId,
        InvoiceStatus status,
        String statusLabel,
        String issuedBy,
        Instant createdAt,
        Instant updatedAt,
        PaymentProofResponse paymentProof,
        ReceiptResponse receipt
) {
    public static InvoiceResponse from(
            Invoice invoice,
            PaymentProofResponse proof,
            ReceiptResponse receipt
    ) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getNumber(),
                invoice.getCustomerEmail(),
                invoice.getCustomerName(),
                invoice.getDepartment(),
                invoice.getDescription(),
                invoice.getAmount(),
                invoice.getCurrency(),
                invoice.getSourceType(),
                invoice.getSourceId(),
                invoice.getStatus(),
                invoice.getStatus().label(),
                invoice.getIssuedBy(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                proof,
                receipt
        );
    }
}
