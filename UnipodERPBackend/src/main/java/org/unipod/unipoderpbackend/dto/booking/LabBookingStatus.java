package org.unipod.unipoderpbackend.dto.booking;

import org.unipod.unipoderpbackend.model.booking.BookingStatus;

/** The two states the lab screen knows (D-042). */
public enum LabBookingStatus {
    CONFIRMED,
    CANCELLED;

    /** The stored statuses a lab booking list shows as confirmed. */
    public static final java.util.List<BookingStatus> CONFIRMED_STORED =
            java.util.List.of(BookingStatus.APPROVED, BookingStatus.CHECKED_IN, BookingStatus.COMPLETED);
}
