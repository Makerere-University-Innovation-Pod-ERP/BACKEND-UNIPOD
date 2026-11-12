package org.unipod.unipoderpbackend.dto.training;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.model.training.TrainingStatus;

/**
 * The frontend's session shape ({@code date} is the start time), plus the fields it does not
 * read yet: {@code departmentId}, {@code endsAt}, {@code fee}, {@code safetyCourse}, {@code published}.
 */
public record SessionResponse(
        UUID id,
        String title,
        String facilitator,
        OffsetDateTime date,
        int enrolledCount,
        int capacity,
        String venue,
        TrainingStatus status,
        String description,
        List<ParticipantResponse> participants,
        UUID departmentId,
        OffsetDateTime endsAt,
        int fee,
        boolean safetyCourse,
        boolean published) {

    /**
     * @param participants the session's non-cancelled participants, which are exactly the ones
     *                     holding a seat
     */
    public static SessionResponse from(
            TrainingSession session, List<TrainingParticipant> participants, Instant now, ZoneId zone) {
        return new SessionResponse(
                session.getId(),
                session.getTitle(),
                session.getFacilitator(),
                session.getStartsAt().atZone(zone).toOffsetDateTime(),
                participants.size(),
                session.getCapacity(),
                session.getVenue(),
                session.statusAt(now),
                session.getDescription(),
                participants.stream().map(participant -> ParticipantResponse.from(participant, zone)).toList(),
                session.getDepartmentId(),
                session.getEndsAt().atZone(zone).toOffsetDateTime(),
                session.getFee(),
                session.isSafetyCourse(),
                session.isPublished());
    }
}
