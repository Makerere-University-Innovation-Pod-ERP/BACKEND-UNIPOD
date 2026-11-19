package org.unipod.unipoderpbackend.model.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.booking.InvalidBookingException;

/** Someone signed up for a textile training booking, and whether they turned up (D-044). */
@Entity
@Table(name = "booking_attendees")
public class BookingAttendee {
    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private boolean attended;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BookingAttendee() {
    }

    public BookingAttendee(UUID id, UUID bookingId, String name, Instant now) {
        if (name == null || name.isBlank()) {
            throw new InvalidBookingException("Enter the participant's name.");
        }
        this.id = id;
        this.bookingId = bookingId;
        this.name = name.strip();
        this.attended = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void recordAttendance(boolean attended, Instant now) {
        this.attended = attended;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getName() {
        return name;
    }

    public boolean isAttended() {
        return attended;
    }
}
