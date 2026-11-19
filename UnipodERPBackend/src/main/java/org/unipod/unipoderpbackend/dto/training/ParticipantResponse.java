package org.unipod.unipoderpbackend.dto.training;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.training.AttendanceStatus;
import org.unipod.unipoderpbackend.model.training.RegistrationStatus;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;

public record ParticipantResponse(
        UUID id,
        String name,
        String regNumber,
        String department,
        String contactEmail,
        String userId,
        RegistrationStatus status,
        AttendanceStatus attendance,
        boolean passed,
        OffsetDateTime passedAt,
        OffsetDateTime registeredAt) {

    public static ParticipantResponse from(TrainingParticipant participant, ZoneId zone) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getName(),
                participant.getRegNumber(),
                participant.getDepartment(),
                participant.getContactEmail(),
                participant.getUserId(),
                participant.getStatus(),
                participant.getAttendance(),
                participant.isPassed(),
                participant.getPassedAt() == null ? null : participant.getPassedAt().atZone(zone).toOffsetDateTime(),
                participant.getRegisteredAt().atZone(zone).toOffsetDateTime());
    }
}
