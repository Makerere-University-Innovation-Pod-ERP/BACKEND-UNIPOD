package org.unipod.unipoderpbackend.dto.finance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.finance.InvoicePaymentProof;
import org.unipod.unipoderpbackend.model.finance.PaymentProofStatus;

public record PaymentProofResponse(
        UUID id,
        String reference,
        long amount,
        LocalDate paidOn,
        String contentType,
        long fileSize,
        String originalFilename,
        PaymentProofStatus status,
        String rejectionReason,
        String uploadedBy,
        Instant uploadedAt,
        String verifiedBy,
        Instant verifiedAt
) {
    public static PaymentProofResponse from(InvoicePaymentProof proof) {
        return new PaymentProofResponse(
                proof.getId(),
                proof.getReference(),
                proof.getAmount(),
                proof.getPaidOn(),
                proof.getContentType(),
                proof.getFileSize(),
                proof.getOriginalFilename(),
                proof.getStatus(),
                proof.getRejectionReason(),
                proof.getUploadedBy(),
                proof.getUploadedAt(),
                proof.getVerifiedBy(),
                proof.getVerifiedAt()
        );
    }
}
