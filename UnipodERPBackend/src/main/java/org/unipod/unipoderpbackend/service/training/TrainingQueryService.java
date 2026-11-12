package org.unipod.unipoderpbackend.service.training;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.training.FollowUpResponse;
import org.unipod.unipoderpbackend.dto.training.SessionResponse;
import org.unipod.unipoderpbackend.dto.training.TrainingOverviewResponse;
import org.unipod.unipoderpbackend.exception.training.TrainingNotFoundException;
import org.unipod.unipoderpbackend.model.training.FollowUpStatus;
import org.unipod.unipoderpbackend.model.training.MentorshipFollowUp;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.model.training.TrainingStatus;
import org.unipod.unipoderpbackend.repository.training.MentorshipFollowUpRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;

@Service
public class TrainingQueryService {
    private final TrainingSessionRepository sessions;
    private final TrainingParticipantRepository participants;
    private final MentorshipFollowUpRepository followUps;
    private final Clock clock;

    public TrainingQueryService(
            TrainingSessionRepository sessions,
            TrainingParticipantRepository participants,
            MentorshipFollowUpRepository followUps,
            Clock clock) {
        this.sessions = sessions;
        this.participants = participants;
        this.followUps = followUps;
        this.clock = clock;
    }

    /**
     * @param departmentId optional filter; follow-ups are not department-owned, so they are never filtered
     */
    @Transactional(readOnly = true)
    public TrainingOverviewResponse overview(UUID departmentId) {
        Instant now = clock.instant();
        List<TrainingSession> listed = departmentId == null
                ? sessions.findAllNewestFirst()
                : sessions.findByDepartmentNewestFirst(departmentId);
        Map<UUID, List<TrainingParticipant>> seated = participantsBySession(listed.stream().map(TrainingSession::getId).toList());
        List<SessionResponse> responses = listed.stream()
                .map(session -> toResponse(session, seated, now))
                .toList();

        long upcoming = responses.stream().filter(session -> session.status() == TrainingStatus.UPCOMING).count();
        List<SessionResponse> open = responses.stream()
                .filter(session -> session.status() == TrainingStatus.UPCOMING
                        || session.status() == TrainingStatus.IN_PROGRESS)
                .toList();
        long seatsFilled = open.stream().mapToLong(SessionResponse::enrolledCount).sum();
        long totalSeats = open.stream().mapToLong(SessionResponse::capacity).sum();

        List<MentorshipFollowUp> allFollowUps = followUps.findAllInScheduleOrder();
        long unfinished = allFollowUps.stream().filter(followUp -> followUp.getStatus() != FollowUpStatus.COMPLETED).count();
        return new TrainingOverviewResponse(
                upcoming,
                seatsFilled,
                totalSeats,
                unfinished,
                responses,
                allFollowUps.stream().map(FollowUpResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public SessionResponse session(UUID id) {
        TrainingSession session = sessions.findById(id).orElseThrow(TrainingNotFoundException::session);
        return toResponse(session, participantsBySession(List.of(id)), clock.instant());
    }

    private Map<UUID, List<TrainingParticipant>> participantsBySession(Collection<UUID> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        return participants.findActiveBySessionIds(sessionIds).stream()
                .collect(Collectors.groupingBy(TrainingParticipant::getSessionId));
    }

    private SessionResponse toResponse(
            TrainingSession session, Map<UUID, List<TrainingParticipant>> seated, Instant now) {
        return SessionResponse.from(session, seated.getOrDefault(session.getId(), List.of()), now, clock.getZone());
    }
}
