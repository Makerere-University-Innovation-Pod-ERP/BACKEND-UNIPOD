package org.unipod.unipoderpbackend.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoice_payment_proofs")
public class InvoicePaymentProof {
    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(nullable = false, length = 120)
    private String reference;

    @Column(nullable = false)
    private long amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "file_reference", nullable = false, length = 256)
    private String fileReference;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentProofStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected InvoicePaymentProof() {
    }

    public InvoicePaymentProof(
            UUID id,
            UUID invoiceId,
            String reference,
            long amount,
            LocalDate paidOn,
            String fileReference,
            String contentType,
            long fileSize,
            String originalFilename,
            String uploadedBy,
            Instant uploadedAt
    ) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.reference = reference;
        this.amount = amount;
        this.paidOn = paidOn;
        this.fileReference = fileReference;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.originalFilename = originalFilename;
        this.status = PaymentProofStatus.PENDING;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public void verify(String actor, Instant now) {
        this.status = PaymentProofStatus.VERIFIED;
        this.rejectionReason = null;
        this.verifiedBy = actor;
        this.verifiedAt = now;
    }

    public void reject(String actor, String reason, Instant now) {
        this.status = PaymentProofStatus.REJECTED;
        this.rejectionReason = reason;
        this.verifiedBy = actor;
        this.verifiedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getReference() {
        return reference;
    }

    public long getAmount() {
        return amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public String getFileReference() {
        return fileReference;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public PaymentProofStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
