package org.unipod.unipoderpbackend.service.training;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.training.CreateFollowUpRequest;
import org.unipod.unipoderpbackend.dto.training.FollowUpResponse;
import org.unipod.unipoderpbackend.dto.training.UpdateFollowUpRequest;
import org.unipod.unipoderpbackend.exception.training.TrainingConflictException;
import org.unipod.unipoderpbackend.exception.training.TrainingNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.training.MentorshipFollowUp;
import org.unipod.unipoderpbackend.repository.training.MentorshipFollowUpRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;

/**
 * Mentorship follow-ups. Audit entries carry ids, status and flags only: the mentee, the mentor,
 * the title and the notes text are personal and stay out of the audit log (D-027).
 */
@Service
public class MentorshipService {
    private static final String FOLLOW_UP_ENTITY = "mentorship_follow_up";

    private final MentorshipFollowUpRepository followUps;
    private final AuditService audit;
    private final Clock clock;

    public MentorshipService(MentorshipFollowUpRepository followUps, AuditService audit, Clock clock) {
        this.followUps = followUps;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public FollowUpResponse create(CreateFollowUpRequest request, Actor actor) {
        MentorshipFollowUp followUp = new MentorshipFollowUp(
                UUID.randomUUID(),
                request.title(),
                request.mentee(),
                request.mentor(),
                request.scheduledDate(),
                request.focus(),
                request.priority(),
                request.notes(),
                clock.instant());
        followUps.saveAndFlush(followUp);
        audit.log(AuditEvent.created(
                actor,
                "training.follow_up.created",
                FOLLOW_UP_ENTITY,
                followUp.getId(),
                Map.of("status", followUp.getStatus().name(),
                        "priority", followUp.getPriority().name(),
                        "scheduledDate", followUp.getScheduledDate().toString())));
        return FollowUpResponse.from(followUp);
    }

    @Transactional
    public FollowUpResponse update(UUID id, UpdateFollowUpRequest request, Actor actor) {
        MentorshipFollowUp followUp = followUps.findById(id).orElseThrow(TrainingNotFoundException::followUp);
        if (request.version() != null && request.version() != followUp.getVersion()) {
            throw staleFollowUp();
        }
        String before = followUp.getStatus().name();
        boolean notesChanged = request.notes() != null && !request.notes().strip().equals(followUp.getNotes());
        followUp.update(request.status(), request.notes(), clock.instant());
        try {
            followUps.saveAndFlush(followUp);
        } catch (ObjectOptimisticLockingFailureException concurrentEdit) {
            throw staleFollowUp();
        }
        audit.log(AuditEvent.changed(
                actor,
                "training.follow_up.updated",
                FOLLOW_UP_ENTITY,
                id,
                Map.of("status", before),
                Map.of("status", followUp.getStatus().name(), "notesChanged", notesChanged)));
        return FollowUpResponse.from(followUp);
    }

    private static TrainingConflictException staleFollowUp() {
        return new TrainingConflictException("This follow-up was changed by someone else. Reload it and try again.");
    }
}
