package org.unipod.unipoderpbackend.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_receipts")
public class InvoiceReceipt {
    @Id
    private UUID id;

    @Column(name = "invoice_id", nullable = false, unique = true)
    private UUID invoiceId;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 32)
    private String receiptNumber;

    @Column(name = "issued_by", nullable = false)
    private String issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected InvoiceReceipt() {
    }

    public InvoiceReceipt(UUID id, UUID invoiceId, String receiptNumber, String issuedBy, Instant issuedAt) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.receiptNumber = receiptNumber;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
