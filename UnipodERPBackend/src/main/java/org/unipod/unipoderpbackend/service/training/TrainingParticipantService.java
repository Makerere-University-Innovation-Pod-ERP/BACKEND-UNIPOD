package org.unipod.unipoderpbackend.service.training;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.training.AttendanceRequest;
import org.unipod.unipoderpbackend.dto.training.SessionResponse;
import org.unipod.unipoderpbackend.exception.training.TrainingNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;

/**
 * Changes to one registration. Every change takes the session row lock, like registration does, so
 * edits to the same session are applied one after the other and never fight over a version.
 * Audit entries carry ids, status and counts only, never names, emails or registration numbers (D-027).
 */
@Service
public class TrainingParticipantService {
    private static final String PARTICIPANT_ENTITY = "training_participant";

    private final TrainingSessionRepository sessions;
    private final TrainingParticipantRepository participants;
    private final AuditService audit;
    private final Clock clock;

    public TrainingParticipantService(
            TrainingSessionRepository sessions,
            TrainingParticipantRepository participants,
            AuditService audit,
            Clock clock) {
        this.sessions = sessions;
        this.participants = participants;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void remove(UUID sessionId, UUID participantId, Actor actor) {
        Registration registration = lock(sessionId, participantId);
        Instant now = clock.instant();
        String before = registration.participant().getStatus().name();
        registration.participant().cancel(now);
        participants.saveAndFlush(registration.participant());
        record(actor, "training.participant.removed", registration,
                Map.of("status", before),
                Map.of("status", registration.participant().getStatus().name(),
                        "sessionId", sessionId.toString(),
                        "seatsHeld", participants.countSeats(sessionId)));
    }

    @Transactional
    public SessionResponse recordAttendance(UUID sessionId, AttendanceRequest request, Actor actor) {
        Registration registration = lock(sessionId, request.participantId());
        Instant now = clock.instant();
        String before = registration.participant().getAttendance().name();
        registration.participant().markAttendance(request.attendance(), now);
        participants.saveAndFlush(registration.participant());
        record(actor, "training.attendance.recorded", registration,
                Map.of("attendance", before),
                Map.of("attendance", request.attendance().name(), "sessionId", sessionId.toString()));
        return view(registration.session(), now);
    }

    /** Until David's invoices exist, a coordinator confirms payment by hand (D-018). */
    @Transactional
    public SessionResponse confirmPayment(UUID sessionId, UUID participantId, Actor actor) {
        Registration registration = lock(sessionId, participantId);
        Instant now = clock.instant();
        String before = registration.participant().getStatus().name();
        registration.participant().confirmPayment(now);
        participants.saveAndFlush(registration.participant());
        record(actor, "training.participant.payment_confirmed", registration,
                Map.of("status", before),
                Map.of("status", registration.participant().getStatus().name(),
                        "sessionId", sessionId.toString(),
                        "fee", registration.session().getFee()));
        return view(registration.session(), now);
    }

    @Transactional
    public SessionResponse markPassed(UUID sessionId, UUID participantId, Actor actor) {
        Registration registration = lock(sessionId, participantId);
        Instant now = clock.instant();
        registration.participant().markPassed(registration.session().isSafetyCourse(), now);
        participants.saveAndFlush(registration.participant());
        record(actor, "training.participant.passed", registration,
                Map.of("passed", false),
                Map.of("passed", true, "sessionId", sessionId.toString()));
        return view(registration.session(), now);
    }

    private SessionResponse view(TrainingSession session, Instant now) {
        return SessionResponse.from(
                session, participants.findActiveBySessionIds(List.of(session.getId())), now, clock.getZone());
    }

    private Registration lock(UUID sessionId, UUID participantId) {
        TrainingSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(TrainingNotFoundException::session);
        TrainingParticipant participant = participants.findActive(sessionId, participantId)
                .orElseThrow(TrainingNotFoundException::participant);
        return new Registration(session, participant);
    }

    private void record(
            Actor actor, String action, Registration registration, Map<String, Object> before,
            Map<String, Object> after) {
        audit.log(new AuditEvent(
                actor,
                action,
                PARTICIPANT_ENTITY,
                registration.participant().getId(),
                before,
                after,
                registration.session().getDepartmentId()));
    }

    private record Registration(TrainingSession session, TrainingParticipant participant) {
    }
}
