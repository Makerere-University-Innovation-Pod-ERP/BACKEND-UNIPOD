package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.dto.booking.TextileBookingResponse;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.BookingAttendee;
import org.unipod.unipoderpbackend.model.booking.DepartmentBookingDetail;
import org.unipod.unipoderpbackend.repository.booking.BookingAttendeeRepository;
import org.unipod.unipoderpbackend.repository.booking.DepartmentBookingDetailRepository;

/** Builds the workshop screen's bookings with their detail rows and sign-ups, in two queries however many there are. */
@Component
class TextileBookingViewer {
    private final DepartmentBookingDetailRepository details;
    private final BookingAttendeeRepository attendees;
    private final Clock clock;

    TextileBookingViewer(DepartmentBookingDetailRepository details, BookingAttendeeRepository attendees, Clock clock) {
        this.details = details;
        this.attendees = attendees;
        this.clock = clock;
    }

    List<TextileBookingResponse> views(List<Booking> bookings) {
        List<UUID> ids = bookings.stream().map(Booking::getId).toList();
        Map<UUID, DepartmentBookingDetail> detailById = details.findAllById(ids).stream()
                .collect(Collectors.toMap(DepartmentBookingDetail::getBookingId, Function.identity()));
        Map<UUID, List<BookingAttendee>> attendeesByBooking = attendees
                .findAllByBookingIdInOrderByCreatedAtAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(BookingAttendee::getBookingId));
        return bookings.stream()
                .map(booking -> TextileBookingResponse.from(
                        booking,
                        detailById.get(booking.getId()),
                        attendeesByBooking.getOrDefault(booking.getId(), List.of()),
                        clock.getZone()))
                .toList();
    }

    TextileBookingResponse view(Booking booking) {
        return views(List.of(booking)).get(0);
    }
}
