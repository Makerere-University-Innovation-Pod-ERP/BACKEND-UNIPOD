package org.unipod.unipoderpbackend.dto.booking;

import java.util.List;

/** The workshop screen's week: bookings plus the weekly classes derived from the timetable (D-043). */
public record TextileCalendarResponse(List<TextileBookingResponse> bookings, List<TimetableEntryResponse> timetable) {
}
