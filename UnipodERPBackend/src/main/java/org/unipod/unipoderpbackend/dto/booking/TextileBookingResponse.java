package org.unipod.unipoderpbackend.dto.booking;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.BookingAttendee;
import org.unipod.unipoderpbackend.model.booking.DepartmentBookingDetail;

/**
 * A workshop or training booking exactly as the frontend's textile {@code bookingSchema} reads it.
 * {@code date}, {@code start} and {@code end} are Kampala local; {@code machineId} and {@code capacity}
 * are null when not set.
 */
public record TextileBookingResponse(
        String id,
        TextileKind kind,
        String date,
        String start,
        String end,
        String purpose,
        String machineId,
        Integer capacity,
        String bookedBy,
        TextileBookingStatus status,
        List<AttendeeResponse> attendees) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static TextileBookingResponse from(
            Booking booking, DepartmentBookingDetail detail, List<BookingAttendee> attendees, ZoneId zone) {
        var starts = booking.getStartsAt().atZone(zone);
        var ends = booking.getEndsAt().atZone(zone);
        return new TextileBookingResponse(
                booking.getId().toString(),
                TextileKind.from(detail.getKind()),
                starts.toLocalDate().toString(),
                starts.format(TIME),
                ends.format(TIME),
                booking.getPurpose(),
                detail.getMachineId(),
                detail.getCapacity(),
                detail.getBookedBy(),
                TextileBookingStatus.shown(booking.getStatus()).orElseThrow(),
                attendees.stream().map(AttendeeResponse::from).toList());
    }
}
