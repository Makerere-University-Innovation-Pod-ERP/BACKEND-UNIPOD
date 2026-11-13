package org.unipod.unipoderpbackend.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.finance.InvoiceConflictException;

@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String number;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(length = 100)
    private String department;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency = "UGX";

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private InvoiceSourceType sourceType;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InvoiceStatus status;

    @Column(name = "issued_by", nullable = false)
    private String issuedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Invoice() {
    }

    public Invoice(
            UUID id,
            String number,
            String customerEmail,
            String customerName,
            String department,
            String description,
            long amount,
            InvoiceSourceType sourceType,
            String sourceId,
            String issuedBy,
            Instant now
    ) {
        this.id = id;
        this.number = number;
        this.customerEmail = normaliseEmail(customerEmail);
        this.customerName = customerName;
        this.department = department;
        this.description = description;
        this.amount = amount;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.status = InvoiceStatus.WAITING_FOR_PROOF;
        this.issuedBy = issuedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void paymentProofUploaded(Instant now) {
        requireStatus(InvoiceStatus.WAITING_FOR_PROOF);
        this.status = InvoiceStatus.WAITING_FOR_CHECK;
        this.updatedAt = now;
    }

    public void paymentRejected(Instant now) {
        requireStatus(InvoiceStatus.WAITING_FOR_CHECK);
        this.status = InvoiceStatus.WAITING_FOR_PROOF;
        this.updatedAt = now;
    }

    public void paymentVerified(Instant now) {
        requireStatus(InvoiceStatus.WAITING_FOR_CHECK);
        this.status = InvoiceStatus.PAID;
        this.updatedAt = now;
    }

    public void receipted(Instant now) {
        requireStatus(InvoiceStatus.PAID);
        this.status = InvoiceStatus.RECEIPTED;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(String email) {
        return email != null && customerEmail.equals(normaliseEmail(email));
    }

    private void requireStatus(InvoiceStatus expected) {
        if (status != expected) {
            throw new InvoiceConflictException(
                    "The invoice cannot be changed from status " + status + ".");
        }
    }

    private static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getDepartment() {
        return department;
    }

    public String getDescription() {
        return description;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public InvoiceSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
