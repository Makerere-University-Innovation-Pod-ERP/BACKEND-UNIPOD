package org.unipod.unipoderpbackend.dto.booking;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;
import org.unipod.unipoderpbackend.model.booking.DepartmentBookingDetail;
import org.unipod.unipoderpbackend.model.operations.PaymentStatus;

/**
 * A lab booking exactly as the frontend's {@code labBookingSchema} reads it. {@code date}, {@code start}
 * and {@code end} are Kampala local; {@code createdAt} is ISO-8601 with offset.
 */
public record LabBookingResponse(
        String id,
        String purpose,
        String date,
        String start,
        String end,
        int groupSize,
        String bookedBy,
        String customerType,
        LabBookingStatus status,
        PaymentStatus paymentStatus,
        OffsetDateTime createdAt) {
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static LabBookingResponse from(
            Booking booking, DepartmentBookingDetail detail, PaymentStatus payment, ZoneId zone) {
        var starts = booking.getStartsAt().atZone(zone);
        var ends = booking.getEndsAt().atZone(zone);
        return new LabBookingResponse(
                booking.getId().toString(),
                booking.getPurpose(),
                starts.toLocalDate().toString(),
                starts.format(TIME),
                ends.format(TIME),
                booking.getAttendees(),
                detail.getBookedBy(),
                detail.getCustomerType() == null ? "Public" : detail.getCustomerType(),
                booking.getStatus() == BookingStatus.CANCELLED ? LabBookingStatus.CANCELLED : LabBookingStatus.CONFIRMED,
                payment,
                booking.getCreatedAt().atZone(zone).toOffsetDateTime());
    }
}
