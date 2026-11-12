package org.unipod.unipoderpbackend.service.training;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.training.CreateSessionRequest;
import org.unipod.unipoderpbackend.dto.training.RegisterParticipantRequest;
import org.unipod.unipoderpbackend.dto.training.SessionResponse;
import org.unipod.unipoderpbackend.exception.training.InvalidTrainingException;
import org.unipod.unipoderpbackend.exception.training.TrainingConflictException;
import org.unipod.unipoderpbackend.exception.training.TrainingNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.model.training.TrainingStatus;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.config.ConfigService;

@Service
public class TrainingSessionService {
    private static final String SESSION_ENTITY = "training_session";
    private static final String PARTICIPANT_ENTITY = "training_participant";

    private final TrainingSessionRepository sessions;
    private final TrainingParticipantRepository participants;
    private final ConfigService config;
    private final AuditService audit;
    private final Clock clock;

    public TrainingSessionService(
            TrainingSessionRepository sessions,
            TrainingParticipantRepository participants,
            ConfigService config,
            AuditService audit,
            Clock clock) {
        this.sessions = sessions;
        this.participants = participants;
        this.config = config;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse create(CreateSessionRequest request, Actor actor) {
        // The foreign key is the backstop; this gives callers a readable reason first.
        boolean activeDepartment = config.departments().stream()
                .anyMatch(department -> department.id().equals(request.departmentId()));
        if (!activeDepartment) {
            throw unknownDepartment();
        }
        Instant now = clock.instant();
        TrainingSession session = new TrainingSession(
                UUID.randomUUID(),
                request.departmentId(),
                request.title(),
                request.facilitator(),
                request.date().toInstant(),
                request.endsAt() == null ? null : request.endsAt().toInstant(),
                request.capacity(),
                request.venue(),
                request.description(),
                request.fee() == null ? 0 : request.fee(),
                Boolean.TRUE.equals(request.safetyCourse()),
                request.publish() == null || request.publish(),
                now);
        try {
            sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException departmentRemoved) {
            throw unknownDepartment();
        }
        audit.log(new AuditEvent(
                actor,
                "training.session.created",
                SESSION_ENTITY,
                session.getId(),
                null,
                sessionSnapshot(session),
                session.getDepartmentId()));
        return SessionResponse.from(session, List.of(), now, clock.getZone());
    }

    /**
     * Takes the session row lock before counting, so two requests for the last seat are served one
     * after the other and the second sees the seat gone (D-007).
     */
    @Transactional
    public SessionResponse registerParticipant(UUID sessionId, RegisterParticipantRequest request, Actor actor) {
        TrainingSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(TrainingNotFoundException::session);
        Instant now = clock.instant();
        if (session.statusAt(now) == TrainingStatus.COMPLETED) {
            throw new TrainingConflictException("Registration is closed for this session.");
        }
        String regNumber = request.regNumber().strip();
        if (participants.existsActiveRegNumber(sessionId, regNumber)) {
            throw regNumberTaken();
        }
        long seatsHeld = participants.countSeats(sessionId);
        if (seatsHeld >= session.getCapacity()) {
            throw new TrainingConflictException("This session is full.");
        }
        TrainingParticipant participant = new TrainingParticipant(
                UUID.randomUUID(),
                sessionId,
                request.name(),
                regNumber,
                request.department(),
                request.contactEmail(),
                session.getFee(),
                now);
        try {
            participants.saveAndFlush(participant);
        } catch (DataIntegrityViolationException duplicate) {
            // The lock already serialises registrations; the unique index is the last line of defence.
            throw regNumberTaken();
        }
        audit.log(new AuditEvent(
                actor,
                "training.participant.registered",
                PARTICIPANT_ENTITY,
                participant.getId(),
                null,
                // Ids, status and counts only: names, emails and registration numbers stay out of the audit log (D-027).
                Map.of(
                        "sessionId", sessionId.toString(),
                        "status", participant.getStatus().name(),
                        "seatsHeld", seatsHeld + 1,
                        "capacity", session.getCapacity()),
                session.getDepartmentId()));
        return SessionResponse.from(
                session, participants.findActiveBySessionIds(List.of(sessionId)), now, clock.getZone());
    }

    // The facilitator is a named person, so audit snapshots leave the name out (D-027).
    private static Map<String, Object> sessionSnapshot(TrainingSession session) {
        return Map.of(
                "title", session.getTitle(),
                "startsAt", session.getStartsAt().toString(),
                "endsAt", session.getEndsAt().toString(),
                "capacity", session.getCapacity(),
                "fee", session.getFee(),
                "safetyCourse", session.isSafetyCourse(),
                "published", session.isPublished());
    }

    private static InvalidTrainingException unknownDepartment() {
        return new InvalidTrainingException("Choose an active department.");
    }

    private static TrainingConflictException regNumberTaken() {
        return new TrainingConflictException("This registration number is already registered for this session.");
    }
}
