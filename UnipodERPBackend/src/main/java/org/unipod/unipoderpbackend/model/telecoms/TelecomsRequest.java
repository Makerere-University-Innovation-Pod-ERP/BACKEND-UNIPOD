package org.unipod.unipoderpbackend.model.telecoms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestPayload;
import org.unipod.unipoderpbackend.exception.operations.OperationsConflictException;

@Entity
@Table(name = "telecoms_requests")
public class TelecomsRequest {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "customer_type", nullable = false, length = 40)
    private String customerType;

    @Column(name = "service_wanted", nullable = false, length = 2000)
    private String serviceWanted;

    @Column(name = "equipment_involved", nullable = false, length = 1000)
    private String equipmentInvolved;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "rules_accepted", nullable = false)
    private boolean rulesAccepted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TelecomsRequestStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String estimate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String quote;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 24)
    private TelecomsPaymentStatus paymentStatus = TelecomsPaymentStatus.NOT_REQUESTED;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "payment_confirmed_at")
    private Instant paymentConfirmedAt;

    @Column(nullable = false, length = 200)
    private String technician = "";

    @Column(name = "parts_used", nullable = false, length = 2000)
    private String partsUsed = "";

    @Column(name = "time_spent", nullable = false, length = 200)
    private String timeSpent = "";

    @Column(name = "work_notes", nullable = false, length = 5000)
    private String workNotes = "";

    @Column(name = "created_by", nullable = false, length = 254, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected TelecomsRequest() {
    }

    public TelecomsRequest(
            UUID id,
            String code,
            TelecomsRequestPayload payload,
            String createdBy,
            TelecomsRequestStatus initialStatus,
            String estimate,
            Instant now
    ) {
        this.id = id;
        this.code = code;
        this.createdBy = createdBy;
        this.status = initialStatus;
        this.createdAt = now;
        this.updatedAt = now;
        apply(payload, estimate);
    }

    public void updateDraft(TelecomsRequestPayload payload, String estimate, Instant now) {
        requireOneOf(
                "Only a draft, a returned request, or one awaiting details can be edited.",
                TelecomsRequestStatus.DRAFT,
                TelecomsRequestStatus.RETURNED,
                TelecomsRequestStatus.AWAITING_DETAILS
        );
        apply(payload, estimate);
        updatedAt = now;
    }

    public void submitDraft(TelecomsRequestPayload payload, String estimate, Instant now) {
        requireOneOf(
                "Only a draft, a returned request, or one awaiting details can be submitted.",
                TelecomsRequestStatus.DRAFT,
                TelecomsRequestStatus.RETURNED,
                TelecomsRequestStatus.AWAITING_DETAILS
        );
        apply(payload, estimate);
        status = TelecomsRequestStatus.NEW;
        updatedAt = now;
    }

    public void sendQuote(String estimate, String quote, Instant now) {
        requireOneOf(
                "A quote can only be sent for a new request or one awaiting customer details.",
                TelecomsRequestStatus.NEW,
                TelecomsRequestStatus.AWAITING_DETAILS
        );
        this.estimate = estimate;
        this.quote = quote;
        status = TelecomsRequestStatus.QUOTED;
        paymentStatus = TelecomsPaymentStatus.AWAITING_PROOF;
        paymentReference = null;
        paymentConfirmedAt = null;
        updatedAt = now;
    }

    public void confirmPayment(String reference, Instant now) {
        requireStatus(TelecomsRequestStatus.QUOTED, "Payment can only be confirmed for a quoted request.");
        if (paymentStatus != TelecomsPaymentStatus.AWAITING_PROOF
                && paymentStatus != TelecomsPaymentStatus.PROOF_SUBMITTED) {
            throw new OperationsConflictException(
                    "PAYMENT_STATE_INVALID",
                    "Payment cannot be confirmed from " + paymentStatus + "."
            );
        }
        paymentStatus = TelecomsPaymentStatus.CONFIRMED;
        paymentReference = reference == null || reference.isBlank() ? null : reference.trim();
        paymentConfirmedAt = now;
        updatedAt = now;
    }

    public void requestDetails(Instant now) {
        requireOneOf(
                "Details can only be requested for a new request or one awaiting customer details.",
                TelecomsRequestStatus.NEW,
                TelecomsRequestStatus.AWAITING_DETAILS
        );
        status = TelecomsRequestStatus.AWAITING_DETAILS;
        updatedAt = now;
    }

    public void sendBack(Instant now) {
        requireOneOf(
                "Only a new request or one awaiting customer details can be sent back.",
                TelecomsRequestStatus.NEW,
                TelecomsRequestStatus.AWAITING_DETAILS
        );
        status = TelecomsRequestStatus.RETURNED;
        updatedAt = now;
    }

    public void moveTo(TelecomsRequestStatus target, Instant now) {
        boolean scheduling = status == TelecomsRequestStatus.QUOTED && target == TelecomsRequestStatus.SCHEDULED;
        boolean startingWork = status == TelecomsRequestStatus.SCHEDULED
                && target == TelecomsRequestStatus.IN_PROGRESS;
        if (!scheduling && !startingWork) {
            throw invalidTransition(target);
        }
        if (scheduling) {
            if (paymentStatus != TelecomsPaymentStatus.CONFIRMED) {
                throw new OperationsConflictException(
                        "PAYMENT_REQUIRED",
                        "A request can only be scheduled once Finance confirms payment."
                );
            }
        }
        status = target;
        updatedAt = now;
    }

    public void updateWorkDetails(String technician, String partsUsed, String timeSpent, String notes, Instant now) {
        requireStatus(TelecomsRequestStatus.IN_PROGRESS, "Work details can only be updated while work is in progress.");
        this.technician = technician;
        this.partsUsed = partsUsed;
        this.timeSpent = timeSpent;
        this.workNotes = notes;
        updatedAt = now;
    }

    public void markDone(Instant now) {
        requireStatus(TelecomsRequestStatus.IN_PROGRESS, "Only in-progress work can be marked done.");
        if (workNotes == null || workNotes.isBlank()) {
            throw new OperationsConflictException(
                    "WORK_DETAILS_REQUIRED",
                    "Record technician notes and work details before marking the request done."
            );
        }
        status = TelecomsRequestStatus.DONE;
        updatedAt = now;
    }

    private void apply(TelecomsRequestPayload payload, String estimate) {
        customerName = normalized(payload.customerName());
        customerType = payload.customerType() == null || payload.customerType().isBlank()
                ? "Student"
                : payload.customerType().trim();
        serviceWanted = normalized(payload.serviceWanted());
        equipmentInvolved = normalized(payload.equipmentInvolved());
        preferredDate = payload.preferredDate();
        rulesAccepted = payload.rulesAccepted();
        this.estimate = estimate;
    }

    private void requireStatus(TelecomsRequestStatus expected, String message) {
        if (status != expected) {
            throw new OperationsConflictException("INVALID_REQUEST_TRANSITION", message);
        }
    }

    private void requireOneOf(String message, TelecomsRequestStatus... allowed) {
        if (Arrays.stream(allowed).noneMatch(value -> value == status)) {
            throw new OperationsConflictException("INVALID_REQUEST_TRANSITION", message);
        }
    }

    private OperationsConflictException invalidTransition(TelecomsRequestStatus target) {
        return new OperationsConflictException(
                "INVALID_REQUEST_TRANSITION",
                "A request in " + status + " cannot move to " + target + "."
        );
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getCustomerName() { return customerName; }
    public String getCustomerType() { return customerType; }
    public String getServiceWanted() { return serviceWanted; }
    public String getEquipmentInvolved() { return equipmentInvolved; }
    public LocalDate getPreferredDate() { return preferredDate; }
    public boolean isRulesAccepted() { return rulesAccepted; }
    public TelecomsRequestStatus getStatus() { return status; }
    public String getEstimate() { return estimate; }
    public String getQuote() { return quote; }
    public TelecomsPaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getPaymentReference() { return paymentReference; }
    public Instant getPaymentConfirmedAt() { return paymentConfirmedAt; }
    public String getTechnician() { return technician; }
    public String getPartsUsed() { return partsUsed; }
    public String getTimeSpent() { return timeSpent; }
    public String getWorkNotes() { return workNotes; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
