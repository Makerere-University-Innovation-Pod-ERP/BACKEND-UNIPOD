package org.unipod.unipoderpbackend.dto.booking;

/** A weekly class slot the lab or workshop is not free in: the frontend's {@code timetableEntrySchema}. */
public record TimetableEntryResponse(String id, int weekday, String start, String end, String label) {
}
