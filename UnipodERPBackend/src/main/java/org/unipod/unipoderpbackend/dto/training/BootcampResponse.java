package org.unipod.unipoderpbackend.dto.training;

import java.time.OffsetDateTime;
import java.util.List;
import org.unipod.unipoderpbackend.model.operations.PaymentStatus;

/**
 * A Telecoms bootcamp exactly as the frontend's {@code bootcampSessionSchema} reads it (D-045).
 * {@code date}, {@code start} and {@code end} are Kampala local; {@code studentNumber} is null for someone
 * enrolled without one.
 */
public record BootcampResponse(
        String id,
        String topic,
        String instructor,
        String date,
        String start,
        String end,
        int capacity,
        int fee,
        List<Participant> enrolled,
        List<AttendanceEntry> attendance) {

    public record Participant(
            String id,
            String name,
            String email,
            String studentNumber,
            PaymentStatus paymentStatus,
            OffsetDateTime enrolledAt) {
    }

    public record AttendanceEntry(String participantId, BootcampAttendance status) {
    }
}
