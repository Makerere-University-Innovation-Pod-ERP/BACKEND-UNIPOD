package org.unipod.unipoderpbackend.model.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.booking.BookingConflictException;
import org.unipod.unipoderpbackend.exception.booking.InvalidBookingException;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

/**
 * What a lab or textile booking needs beyond the {@link Booking} itself (D-041): which department it
 * belongs to, who it was booked for, and the workshop details. The booking row stays the single source
 * of truth for time, facility and status.
 */
@Entity
@Table(name = "department_booking_details")
public class DepartmentBookingDetail {
    @Id
    @Column(name = "booking_id")
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DepartmentCode department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingKind kind;

    @Column(name = "booked_by", nullable = false, length = 254)
    private String bookedBy;

    @Column(name = "customer_type", length = 64)
    private String customerType;

    @Column(name = "machine_id", length = 64)
    private String machineId;

    private Integer capacity;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by", length = 254)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DepartmentBookingDetail() {
    }

    public DepartmentBookingDetail(
            UUID bookingId,
            DepartmentCode department,
            BookingKind kind,
            String bookedBy,
            String customerType,
            String machineId,
            Integer capacity,
            Instant now) {
        if (bookedBy == null || bookedBy.isBlank()) {
            throw new InvalidBookingException("A booking needs to say who it is for.");
        }
        if (capacity != null && (capacity <= 0 || kind != BookingKind.TRAINING)) {
            throw new InvalidBookingException("Only a training session can have a capacity, and it must be at least 1.");
        }
        this.bookingId = bookingId;
        this.department = department;
        this.kind = kind;
        this.bookedBy = bookedBy.strip();
        this.customerType = clean(customerType);
        this.machineId = clean(machineId);
        this.capacity = capacity;
        this.createdAt = now;
    }

    /** Whether the booking takes sign-ups: only training sessions do. */
    public boolean takesSignUps() {
        return kind == BookingKind.TRAINING;
    }

    public boolean isFullAt(long signedUp) {
        return capacity != null && signedUp >= capacity;
    }

    public void recordCancellation(String reason, String actor, Instant now) {
        if (cancelledAt != null) {
            throw new BookingConflictException("This booking is already cancelled.");
        }
        this.cancelReason = reason;
        this.cancelledBy = actor;
        this.cancelledAt = now;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public DepartmentCode getDepartment() {
        return department;
    }

    public BookingKind getKind() {
        return kind;
    }

    public String getBookedBy() {
        return bookedBy;
    }

    public String getCustomerType() {
        return customerType;
    }

    public String getMachineId() {
        return machineId;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
