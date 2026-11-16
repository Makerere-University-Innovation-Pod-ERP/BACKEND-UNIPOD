package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "media_contracts")
public class MediaContract {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 255)
    private String client;
    @Column(name = "event_name", nullable = false, length = 255)
    private String eventName;
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;
    @Column(length = 255)
    private String venue;
    @Column(name = "agreed_price", nullable = false)
    private long agreedPrice;
    @Column(nullable = false, length = 3)
    private String currency = "UGX";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "milestone_payment_dates", nullable = false, columnDefinition = "jsonb")
    private String milestonePaymentDates = "[]";
    @Column(name = "signed_contract_doc_id")
    private UUID signedContractDocId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assigned_staff", nullable = false, columnDefinition = "jsonb")
    private String assignedStaff = "[]";
    @Column(nullable = false, length = 32)
    private String status = "DRAFT";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "invoice_ids", nullable = false, columnDefinition = "jsonb")
    private String invoiceIds = "[]";
    @Column(name = "created_by", nullable = false, length = 254)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version private Long version;

    protected MediaContract() {}
    public MediaContract(UUID id, String code, String client, String eventName, LocalDate eventDate, String venue,
                         long agreedPrice, String currency, String milestonePaymentDates, UUID signedContractDocId,
                         String assignedStaff, String createdBy, Instant now) {
        this.id = id; this.code = code; this.client = client; this.eventName = eventName; this.eventDate = eventDate;
        this.venue = venue; this.agreedPrice = agreedPrice; this.currency = currency == null ? "UGX" : currency;
        this.milestonePaymentDates = milestonePaymentDates == null ? "[]" : milestonePaymentDates;
        this.signedContractDocId = signedContractDocId; this.assignedStaff = assignedStaff == null ? "[]" : assignedStaff;
        this.createdBy = createdBy; this.createdAt = now; this.updatedAt = now;
    }
    public void setInvoiceIds(String invoiceIds, Instant now) { this.invoiceIds = invoiceIds == null ? "[]" : invoiceIds; this.updatedAt = now; }
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getClient() { return client; }
    public String getEventName() { return eventName; }
    public LocalDate getEventDate() { return eventDate; }
    public String getVenue() { return venue; }
    public long getAgreedPrice() { return agreedPrice; }
    public String getCurrency() { return currency; }
    public String getMilestonePaymentDates() { return milestonePaymentDates; }
    public UUID getSignedContractDocId() { return signedContractDocId; }
    public String getAssignedStaff() { return assignedStaff; }
    public String getStatus() { return status; }
    public String getInvoiceIds() { return invoiceIds; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

