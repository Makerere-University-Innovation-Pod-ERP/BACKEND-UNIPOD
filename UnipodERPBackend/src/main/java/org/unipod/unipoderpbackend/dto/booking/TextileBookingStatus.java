package org.unipod.unipoderpbackend.dto.booking;

import java.util.List;
import java.util.Optional;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;

/** The three states the workshop screen knows (D-042). */
public enum TextileBookingStatus {
    APPROVED,
    PENDING,
    CANCELLED;

    /** The stored statuses a workshop list shows; rejected and equipment-loan states are not workshop bookings. */
    public static final List<BookingStatus> LISTED = List.of(
            BookingStatus.APPROVED, BookingStatus.CHECKED_IN, BookingStatus.COMPLETED,
            BookingStatus.PENDING, BookingStatus.CANCELLED);

    public static Optional<TextileBookingStatus> shown(BookingStatus stored) {
        return switch (stored) {
            case APPROVED, CHECKED_IN, COMPLETED -> Optional.of(APPROVED);
            case PENDING -> Optional.of(PENDING);
            case CANCELLED -> Optional.of(CANCELLED);
            case REJECTED, OUT, RETURNED -> Optional.empty();
        };
    }
}
