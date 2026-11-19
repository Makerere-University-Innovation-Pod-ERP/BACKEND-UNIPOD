package org.unipod.unipoderpbackend.model.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.booking.BookingConflictException;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    @Column(nullable = false, length = 1000)
    private String purpose;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int attendees;

    @Column(length = 254)
    private String operator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "request_type", nullable = false, length = 16)
    private String requestType = "FACILITY";

    @Column(name = "is_equipment_request", nullable = false)
    private boolean equipmentRequest;

    @Column(name = "equipment_name", length = 255)
    private String equipmentName;

    @Column(name = "purpose_detail", length = 2000)
    private String purposeDetail;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "decided_by", length = 254)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "condition_out", length = 32)
    private String conditionOut;

    @Column(name = "condition_returned", length = 32)
    private String conditionReturned;

    @Column(name = "return_notes", length = 2000)
    private String returnNotes;

    protected Booking() {
    }

    public Booking(
            UUID id,
            String reference,
            Facility facility,
            String requesterId,
            String purpose,
            Instant startsAt,
            Instant endsAt,
            int attendees,
            String operator,
            Instant now
    ) {
        this(id, reference, facility, requesterId, purpose, startsAt, endsAt, attendees, operator,
                "FACILITY", false, null, null, now);
    }

    public Booking(
            UUID id,
            String reference,
            Facility facility,
            String requesterId,
            String purpose,
            Instant startsAt,
            Instant endsAt,
            int attendees,
            String operator,
            String requestType,
            boolean equipmentRequest,
            String equipmentName,
            String purposeDetail,
            Instant now
    ) {
        this.id = id;
        this.reference = reference;
        this.facility = facility;
        this.requesterId = requesterId;
        this.purpose = purpose;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.attendees = attendees;
        this.operator = operator;
        this.requestType = requestType == null ? "FACILITY" : requestType;
        this.equipmentRequest = equipmentRequest;
        this.equipmentName = equipmentName;
        this.purposeDetail = purposeDetail;
        this.status = BookingStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void approve(Instant now) {
        approve(null, null, now);
    }

    public void approve(String decisionNote, String actor, Instant now) {
        requireStatus(BookingStatus.PENDING);
        this.status = BookingStatus.APPROVED;
        this.decisionNote = clean(decisionNote);
        this.decidedBy = actor;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    public void approveOut(String decisionNote, String actor, String conditionOut, Instant now) {
        requireStatus(BookingStatus.PENDING);
        this.status = BookingStatus.OUT;
        this.decisionNote = clean(decisionNote);
        this.decidedBy = actor;
        this.decidedAt = now;
        this.conditionOut = clean(conditionOut);
        this.updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        reject(reason, null, now);
    }

    public void reject(String reason, String actor, Instant now) {
        requireStatus(BookingStatus.PENDING);
        this.status = BookingStatus.REJECTED;
        this.rejectionReason = clean(reason);
        this.decisionNote = clean(reason);
        this.decidedBy = actor;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    public void markReturned(Instant returnedAt, String conditionReturned, String notes, Instant now) {
        if (!equipmentRequest || status != BookingStatus.OUT) {
            throw new BookingConflictException("Only an out allocation can be returned.");
        }
        this.status = BookingStatus.RETURNED;
        this.returnedAt = returnedAt == null ? now : returnedAt;
        this.conditionReturned = clean(conditionReturned);
        this.returnNotes = clean(notes);
        this.updatedAt = now;
    }

    public void reschedule(Instant startsAt, Instant endsAt, Instant now) {
        if (status != BookingStatus.PENDING && status != BookingStatus.APPROVED) {
            throw new BookingConflictException("Only pending or approved bookings can be rescheduled.");
        }
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.updatedAt = now;
    }

    public void checkIn(Instant now) {
        requireStatus(BookingStatus.APPROVED);
        this.status = BookingStatus.CHECKED_IN;
        this.updatedAt = now;
    }

    public void checkOut(Instant now) {
        requireStatus(BookingStatus.CHECKED_IN);
        this.status = BookingStatus.COMPLETED;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status == BookingStatus.COMPLETED || status == BookingStatus.CANCELLED
                || status == BookingStatus.RETURNED || status == BookingStatus.REJECTED
                || status == BookingStatus.OUT) {
            throw new BookingConflictException("The booking cannot be cancelled in its current state.");
        }
        this.status = BookingStatus.CANCELLED;
        this.updatedAt = now;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private void requireStatus(BookingStatus expected) {
        if (status != expected) {
            throw new BookingConflictException(
                    "The booking cannot be changed from status " + status + ".");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Facility getFacility() {
        return facility;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public String getPurpose() {
        return purpose;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getAttendees() {
        return attendees;
    }

    public String getOperator() {
        return operator;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getRequestType() { return requestType; }
    public boolean isEquipmentRequest() { return equipmentRequest; }
    public String getEquipmentName() { return equipmentName; }
    public String getPurposeDetail() { return purposeDetail; }
    public String getDecisionNote() { return decisionNote; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getReturnedAt() { return returnedAt; }
    public String getConditionOut() { return conditionOut; }
    public String getConditionReturned() { return conditionReturned; }
    public String getReturnNotes() { return returnNotes; }
}
