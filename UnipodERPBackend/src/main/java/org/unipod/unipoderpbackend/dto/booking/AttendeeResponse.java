package org.unipod.unipoderpbackend.dto.booking;

import org.unipod.unipoderpbackend.model.booking.BookingAttendee;

/** The frontend's {@code attendeeSchema}. */
public record AttendeeResponse(String id, String name, boolean attended) {
    public static AttendeeResponse from(BookingAttendee attendee) {
        return new AttendeeResponse(attendee.getId().toString(), attendee.getName(), attendee.isAttended());
    }
}
