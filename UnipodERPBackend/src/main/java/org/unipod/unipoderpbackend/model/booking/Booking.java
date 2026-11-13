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
        this.id = id;
        this.reference = reference;
        this.facility = facility;
        this.requesterId = requesterId;
        this.purpose = purpose;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.attendees = attendees;
        this.operator = operator;
        this.status = BookingStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void approve(Instant now) {
        requireStatus(BookingStatus.PENDING);
        this.status = BookingStatus.APPROVED;
        this.updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        requireStatus(BookingStatus.PENDING);
        this.status = BookingStatus.REJECTED;
        this.rejectionReason = reason;
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
        if (status == BookingStatus.COMPLETED || status == BookingStatus.CANCELLED) {
            throw new BookingConflictException("The booking cannot be cancelled in its current state.");
        }
        this.status = BookingStatus.CANCELLED;
        this.updatedAt = now;
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
}
