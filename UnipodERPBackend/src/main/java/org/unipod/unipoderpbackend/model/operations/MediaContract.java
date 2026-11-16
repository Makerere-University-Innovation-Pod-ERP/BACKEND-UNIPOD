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
        this.venue = venue; this.agreedPrice = agreedPrice;
        this.currency = currency == null || currency.isBlank() ? "UGX" : currency.strip().toUpperCase(java.util.Locale.ROOT);
        this.milestonePaymentDates = milestonePaymentDates == null ? "[]" : milestonePaymentDates;
        this.signedContractDocId = signedContractDocId; this.assignedStaff = assignedStaff == null ? "[]" : assignedStaff;
        this.createdBy = createdBy; this.createdAt = now; this.updatedAt = now;
    }
    public void setInvoiceIds(String invoiceIds, Instant now) { this.invoiceIds = invoiceIds == null ? "[]" : invoiceIds; this.updatedAt = now; }
    public void transitionTo(MediaContractStatus target, UUID signedContractDocId, Instant now) {
        boolean valid = switch (status) {
            case "DRAFT" -> target == MediaContractStatus.SIGNED || target == MediaContractStatus.CANCELLED;
            case "SIGNED" -> target == MediaContractStatus.IN_PROGRESS || target == MediaContractStatus.CANCELLED;
            case "IN_PROGRESS" -> target == MediaContractStatus.COMPLETED || target == MediaContractStatus.CANCELLED;
            default -> false;
        };
        if (!valid) {
            throw new org.unipod.unipoderpbackend.exception.operations.OperationsConflictException(
                    "INVALID_CONTRACT_TRANSITION", "The media contract cannot move from " + status + " to " + target + ".");
        }
        if (target == MediaContractStatus.SIGNED) {
            UUID documentId = signedContractDocId == null ? this.signedContractDocId : signedContractDocId;
            if (documentId == null) {
                throw new org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException(
                        "A signed contract document is required to mark the contract as signed.");
            }
            this.signedContractDocId = documentId;
        } else if (signedContractDocId != null) {
            throw new org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException(
                    "A signed contract document can only be supplied when signing the contract.");
        }
        this.status = target.name();
        this.updatedAt = now;
    }
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

