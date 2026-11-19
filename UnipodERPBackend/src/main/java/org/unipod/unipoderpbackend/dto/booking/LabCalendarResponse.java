package org.unipod.unipoderpbackend.dto.booking;

import java.util.List;

/** The lab screen's week: bookings plus the weekly classes derived from the timetable (D-043). */
public record LabCalendarResponse(List<LabBookingResponse> bookings, List<TimetableEntryResponse> timetable) {
}
