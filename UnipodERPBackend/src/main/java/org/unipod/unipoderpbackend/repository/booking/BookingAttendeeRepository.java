package org.unipod.unipoderpbackend.repository.booking;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.booking.BookingAttendee;

public interface BookingAttendeeRepository extends JpaRepository<BookingAttendee, UUID> {
    // Matches ix_booking_attendees_booking.
    List<BookingAttendee> findAllByBookingIdInOrderByCreatedAtAscIdAsc(Collection<UUID> bookingIds);

    long countByBookingId(UUID bookingId);

    Optional<BookingAttendee> findByIdAndBookingId(UUID id, UUID bookingId);
}
