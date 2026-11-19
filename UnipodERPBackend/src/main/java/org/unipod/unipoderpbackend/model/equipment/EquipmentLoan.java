package org.unipod.unipoderpbackend.model.equipment;

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
import org.unipod.unipoderpbackend.exception.equipment.EquipmentConflictException;
import org.unipod.unipoderpbackend.exception.equipment.InvalidEquipmentException;

/**
 * A request to take equipment out of the lab. It starts PENDING_APPROVAL and a manager either activates it (the
 * equipment is then on loan) or declines it; an active loan ends when the equipment is returned. The loan itself
 * does not touch the equipment's counts: {@code LoanService} reserves and releases units on the item in the same
 * transaction, holding the row lock.
 */
@Entity
@Table(name = "equipment_loans")
public class EquipmentLoan {
    // Keeps dates inside what the database can store and what staff could plausibly enter.
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2999, 12, 31);
    public static final int BORROWER_MAX = 200;
    public static final int BORROWER_TYPE_MAX = 40;
    public static final int CONDITION_MAX = 1000;
    public static final int RETURN_CONDITION_MAX = 2000;
    public static final int NOTE_MAX = 1000;

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "borrower_name", nullable = false, length = BORROWER_MAX)
    private String borrowerName;

    @Column(name = "borrower_type", nullable = false, length = BORROWER_TYPE_MAX)
    private String borrowerType;

    @Column(name = "borrower_contact", nullable = false, length = BORROWER_MAX)
    private String borrowerContact;

    @Column(name = "date_out", nullable = false)
    private LocalDate dateOut;

    @Column(name = "date_back", nullable = false)
    private LocalDate dateBack;

    @Column(name = "outgoing_condition", nullable = false, length = CONDITION_MAX)
    private String outgoingCondition;

    @Column(name = "incoming_condition", length = RETURN_CONDITION_MAX)
    private String incomingCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LoanStatus status;

    @Column(name = "requested_by", nullable = false, length = 254)
    private String requestedBy;

    @Column(name = "decided_by", length = 254)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = NOTE_MAX)
    private String decisionNote;

    @Column(name = "returned_by", length = 254)
    private String returnedBy;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected EquipmentLoan() {
    }

    private EquipmentLoan(UUID id, String code, UUID equipmentId, int quantity, String borrowerName,
                          String borrowerType, String borrowerContact, LocalDate dateOut, LocalDate dateBack,
                          String outgoingCondition, String requestedBy, Instant now) {
        this.id = id;
        this.code = code;
        this.equipmentId = equipmentId;
        this.quantity = quantity;
        this.borrowerName = borrowerName;
        this.borrowerType = borrowerType;
        this.borrowerContact = borrowerContact;
        this.dateOut = dateOut;
        this.dateBack = dateBack;
        this.outgoingCondition = outgoingCondition;
        this.status = LoanStatus.PENDING_APPROVAL;
        this.requestedBy = requestedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static EquipmentLoan request(UUID id, String code, UUID equipmentId, int quantity, String borrowerName,
                                        String borrowerType, String borrowerContact, LocalDate dateOut,
                                        LocalDate dateBack, String outgoingCondition, String requestedBy, Instant now) {
        if (quantity < 1) {
            throw new InvalidEquipmentException("The quantity must be at least 1.");
        }
        if (outOfRange(dateOut) || outOfRange(dateBack)) {
            throw new InvalidEquipmentException("Dates must fall between the years 1900 and 2999.");
        }
        if (dateBack.isBefore(dateOut)) {
            throw new InvalidEquipmentException("The return date must be on or after the date out.");
        }
        return new EquipmentLoan(id, code, equipmentId, quantity, required(borrowerName, "The borrower"),
                borrowerType == null || borrowerType.isBlank() ? "Student" : borrowerType.trim(),
                required(borrowerContact, "The borrower's contact"), dateOut, dateBack,
                outgoingCondition == null ? "" : outgoingCondition.trim(), requestedBy, now);
    }

    public void approve(String manager, String note, Instant now) {
        requirePending("approved");
        status = LoanStatus.ACTIVE;
        decide(manager, note, now);
    }

    public void decline(String manager, String reason, Instant now) {
        requirePending("declined");
        status = LoanStatus.DECLINED;
        decide(manager, reason, now);
    }

    public void markReturned(String receiver, String condition, Instant now) {
        if (status != LoanStatus.ACTIVE) {
            throw new EquipmentConflictException("Only a loan that is out can be returned.");
        }
        if (condition == null || condition.isBlank()) {
            throw new InvalidEquipmentException("Record the condition the equipment came back in.");
        }
        status = LoanStatus.RETURNED;
        incomingCondition = condition.trim();
        returnedBy = receiver;
        returnedAt = now;
        updatedAt = now;
    }

    /** What the loan is called on this day: an active loan past its return date is OVERDUE. */
    public LoanStatus statusOn(LocalDate today) {
        return status == LoanStatus.ACTIVE && dateBack.isBefore(today) ? LoanStatus.OVERDUE : status;
    }

    private static boolean outOfRange(LocalDate date) {
        return date.isBefore(EARLIEST) || date.isAfter(LATEST);
    }

    private void decide(String manager, String note, Instant now) {
        decidedBy = manager;
        decidedAt = now;
        decisionNote = note == null || note.isBlank() ? null : note.trim();
        updatedAt = now;
    }

    private void requirePending(String what) {
        if (status != LoanStatus.PENDING_APPROVAL) {
            throw new EquipmentConflictException("Only a loan that is waiting for approval can be " + what + ".");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidEquipmentException(label + " is required.");
        }
        return value.trim();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public UUID getEquipmentId() { return equipmentId; }
    public int getQuantity() { return quantity; }
    public String getBorrowerName() { return borrowerName; }
    public String getBorrowerType() { return borrowerType; }
    public String getBorrowerContact() { return borrowerContact; }
    public LocalDate getDateOut() { return dateOut; }
    public LocalDate getDateBack() { return dateBack; }
    public String getOutgoingCondition() { return outgoingCondition; }
    public String getIncomingCondition() { return incomingCondition; }
    public LoanStatus getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
}
