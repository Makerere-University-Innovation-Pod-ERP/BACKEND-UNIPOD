package org.unipod.unipoderpbackend.service.training;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.training.BootcampListResponse;
import org.unipod.unipoderpbackend.dto.training.BootcampResponse;
import org.unipod.unipoderpbackend.dto.training.CreateBootcampRequest;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.operations.DepartmentLookup;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;

/**
 * Telecoms bootcamps are training sessions of the Telecoms department (D-045), so seats, registration and
 * attendance all reuse the training rules. The audit entry holds times, capacity and fee; the topic and the
 * instructor stay out (D-021).
 */
@Service
public class BootcampService {
    private static final String VENUE = "Telecoms Lab";

    private final TrainingSessionRepository sessions;
    private final BootcampView view;
    private final DepartmentAccess access;
    private final DepartmentLookup departments;
    private final AuditService audit;
    private final Clock clock;

    public BootcampService(
            TrainingSessionRepository sessions,
            BootcampView view,
            DepartmentAccess access,
            DepartmentLookup departments,
            AuditService audit,
            Clock clock) {
        this.sessions = sessions;
        this.view = view;
        this.access = access;
        this.departments = departments;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public BootcampListResponse list() {
        List<TrainingSession> found = departments.sectorId(DepartmentCode.TELECOMS)
                .map(sessions::findByDepartmentNewestFirst).orElse(List.of());
        return new BootcampListResponse(view.views(found));
    }

    @Transactional
    public BootcampResponse create(CreateBootcampRequest request, Actor actor) {
        access.requireManage(actor, DepartmentCode.TELECOMS);
        UUID department = access.requireSector(DepartmentCode.TELECOMS);
        Instant starts = request.date().atTime(request.start()).atZone(clock.getZone()).toInstant();
        Instant ends = request.date().atTime(request.end()).atZone(clock.getZone()).toInstant();
        TrainingSession session = new TrainingSession(
                UUID.randomUUID(), department, request.topic(), request.instructor(), starts, ends,
                request.capacity(), VENUE, "", request.fee(), false, true, clock.instant());
        sessions.saveAndFlush(session);
        audit.log(new AuditEvent(actor, "bootcamp.created", "training_session", session.getId(), null,
                Map.of("startsAt", starts.toString(), "endsAt", ends.toString(),
                        "capacity", session.getCapacity(), "fee", session.getFee()),
                department));
        return view.view(session);
    }
}
