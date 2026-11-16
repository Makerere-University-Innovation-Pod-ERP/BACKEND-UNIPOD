package org.unipod.unipoderpbackend.service.training;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.operations.BulkAttendancePassResponse;
import org.unipod.unipoderpbackend.dto.training.AttendanceRequest;
import org.unipod.unipoderpbackend.dto.training.SessionResponse;
import org.unipod.unipoderpbackend.exception.training.TrainingConflictException;
import org.unipod.unipoderpbackend.exception.training.TrainingNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.operations.MachineBookingEligibility;
import org.unipod.unipoderpbackend.model.training.AttendanceStatus;
import org.unipod.unipoderpbackend.model.training.RegistrationStatus;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.operations.MachineBookingEligibilityRepository;
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
    private final MachineBookingEligibilityRepository eligibilities;
    private final AuditService audit;
    private final Clock clock;

    public TrainingParticipantService(
            TrainingSessionRepository sessions,
            TrainingParticipantRepository participants,
            MachineBookingEligibilityRepository eligibilities,
            AuditService audit,
            Clock clock) {
        this.sessions = sessions;
        this.participants = participants;
        this.eligibilities = eligibilities;
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
        if (registration.session().isSafetyCourse()) {
            grantEligibility(registration.session(), registration.participant(), actor.email(), now);
        }
        record(actor, "training.participant.passed", registration,
                Map.of("passed", false),
                Map.of("passed", true, "sessionId", sessionId.toString()));
        return view(registration.session(), now);
    }

    @Transactional
    public BulkAttendancePassResponse bulkPass(UUID sessionId, List<UUID> userIds, Actor actor) {
        TrainingSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(TrainingNotFoundException::session);
        if (!session.isSafetyCourse()) {
            throw new TrainingConflictException("Only safety courses record a pass.");
        }
        Instant now = clock.instant();
        Set<UUID> requested = userIds == null ? Set.of() : new HashSet<>(userIds);
        List<TrainingParticipant> inSession = participants.findActiveBySessionId(sessionId);
        int passedCount = 0;
        int unlocked = 0;
        for (TrainingParticipant p : inSession) {
            boolean match = requested.contains(p.getId())
                    || (p.getUserId() != null && requested.stream().anyMatch(id -> id.toString().equalsIgnoreCase(p.getUserId())));
            if (!match) continue;
            if (p.getStatus() != RegistrationStatus.CONFIRMED) continue;
            if (p.getAttendance() != AttendanceStatus.PRESENT) {
                p.markAttendance(AttendanceStatus.PRESENT, now);
            }
            boolean wasPassed = p.isPassed();
            p.markPassed(true, now);
            if (!wasPassed && p.isPassed()) passedCount++;
            if (grantEligibility(session, p, actor.email(), now)) unlocked++;
        }
        participants.saveAll(inSession);
        audit.log(new AuditEvent(actor, "training.attendance.bulk_pass", "training_session", sessionId, null,
                Map.of("passedCount", passedCount, "eligibleUnlocked", unlocked), session.getDepartmentId()));
        return new BulkAttendancePassResponse(sessionId, passedCount, unlocked);
    }

    private boolean grantEligibility(TrainingSession session, TrainingParticipant participant, String grantedBy, Instant now) {
        String userId = participant.getUserId();
        if (userId == null || userId.isBlank()) {
            // fallback to contact email if linked to a user identity by email
            userId = participant.getContactEmail();
        }
        if (userId == null || userId.isBlank()) return false;
        if (eligibilities.existsByUserIdAndDepartmentIgnoreCase(userId, "CNC")) return false;
        eligibilities.save(new MachineBookingEligibility(UUID.randomUUID(), userId, "CNC", session.getId(), now, grantedBy));
        return true;
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
