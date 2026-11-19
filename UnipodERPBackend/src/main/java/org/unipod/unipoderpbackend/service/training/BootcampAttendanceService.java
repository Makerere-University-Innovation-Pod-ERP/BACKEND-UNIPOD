package org.unipod.unipoderpbackend.service.training;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.training.BootcampResponse;
import org.unipod.unipoderpbackend.dto.training.SaveBootcampAttendanceRequest;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.exception.training.TrainingNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.training.AttendanceStatus;
import org.unipod.unipoderpbackend.model.training.RegistrationStatus;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;

/**
 * Saves a bootcamp's attendance roster in one go, under the session row lock like every other change to a
 * session's participants (D-034). The whole roster is applied or none of it. Training only lets confirmed
 * participants be marked, so an ABSENT entry for someone who has not paid is skipped (the screen sends every
 * enrolled person), while PRESENT for them is refused with 409 (D-045).
 * The audit entry holds counts only.
 */
@Service
public class BootcampAttendanceService {
    private final TrainingSessionRepository sessions;
    private final TrainingParticipantRepository participants;
    private final BootcampView view;
    private final DepartmentAccess access;
    private final AuditService audit;
    private final Clock clock;

    public BootcampAttendanceService(
            TrainingSessionRepository sessions,
            TrainingParticipantRepository participants,
            BootcampView view,
            DepartmentAccess access,
            AuditService audit,
            Clock clock) {
        this.sessions = sessions;
        this.participants = participants;
        this.view = view;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public BootcampResponse save(UUID sessionId, SaveBootcampAttendanceRequest request, Actor actor) {
        access.requireManage(actor, DepartmentCode.TELECOMS);
        UUID telecoms = access.requireSector(DepartmentCode.TELECOMS);
        TrainingSession session = sessions.findByIdForUpdate(sessionId)
                .filter(found -> telecoms.equals(found.getDepartmentId()))
                .orElseThrow(TrainingNotFoundException::session);
        Map<UUID, TrainingParticipant> enrolled = new HashMap<>();
        participants.findActiveBySessionId(sessionId).forEach(participant -> enrolled.put(participant.getId(), participant));

        long distinct = request.attendance().stream().map(SaveBootcampAttendanceRequest.Entry::participantId).distinct().count();
        if (distinct != request.attendance().size()) {
            throw new InvalidOperationsRequestException("Each person can appear only once in the roster.");
        }
        Instant now = clock.instant();
        int present = 0;
        int absent = 0;
        int changed = 0;
        for (SaveBootcampAttendanceRequest.Entry entry : request.attendance()) {
            TrainingParticipant participant = enrolled.get(entry.participantId());
            if (participant == null) {
                throw TrainingNotFoundException.participant();
            }
            AttendanceStatus target = entry.status().toStored();
            if (target == AttendanceStatus.PRESENT) {
                present++;
            } else {
                absent++;
            }
            boolean alreadyShownAsTarget = participant.getAttendance() == target
                    || (target == AttendanceStatus.ABSENT && participant.getAttendance() == AttendanceStatus.EXCUSED);
            boolean unpaidAndAbsent = target == AttendanceStatus.ABSENT
                    && participant.getStatus() != RegistrationStatus.CONFIRMED;
            if (alreadyShownAsTarget || unpaidAndAbsent) {
                continue;
            }
            participant.markAttendance(target, now);
            changed++;
        }
        participants.saveAllAndFlush(enrolled.values());
        if (changed > 0) {
            audit.log(new AuditEvent(actor, "bootcamp.attendance_saved", "training_session", sessionId, null,
                    Map.of("present", present, "absent", absent, "changed", changed),
                    session.getDepartmentId()));
        }
        return view.view(session);
    }
}
