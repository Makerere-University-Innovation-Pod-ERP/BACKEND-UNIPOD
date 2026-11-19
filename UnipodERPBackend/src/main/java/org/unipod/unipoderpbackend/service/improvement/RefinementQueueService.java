package org.unipod.unipoderpbackend.service.improvement;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.improvement.RefinementQueueResponse;
import org.unipod.unipoderpbackend.dto.improvement.RefinementQueueRow;
import org.unipod.unipoderpbackend.dto.improvement.RefinementQueueState;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.RefinementQueueCounters;
import org.unipod.unipoderpbackend.repository.improvement.RefinementQueueEntry;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;

/**
 * The staff queue of briefs being improved. Read-only and not audited: one page query, its count when needed,
 * and one counters query. "Today" is the hub's date (Africa/Kampala) from the shared clock; the same date decides
 * the query's overdue-first order, each row's overdue flag and the overdue counter.
 */
@Service
public class RefinementQueueService {
    static final String QUEUE_ROLES = ImprovementRoles.STAFF_ROLES;
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    private final RefinementRequestRepository requests;
    private final Clock clock;

    public RefinementQueueService(RefinementRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(QUEUE_ROLES)
    public RefinementQueueResponse queue(Pageable pageable, Authentication authentication) {
        boolean staff = ImprovementRoles.has(authentication, "ROLE_FACT_FINDING_OFFICER")
                || ImprovementRoles.has(authentication, "ROLE_INNOVATION_COORDINATOR")
                || ImprovementRoles.has(authentication, "ROLE_ADMINISTRATOR");
        if (!staff) {
            throw new AccessDeniedException("Only fact-finding officers, coordinators and administrators can see "
                    + "the refinement queue.");
        }
        LocalDate today = LocalDate.now(clock.withZone(HUB_ZONE));
        Page<RefinementQueueRow> rows = requests.findQueue(today, pageable).map(entry -> rowOf(entry, today));
        RefinementQueueCounters counters = requests.countQueue(today);
        return new RefinementQueueResponse(PageResponse.from(rows), counters.waitingForSubmitter(),
                counters.overdue(), counters.readyToAccept(), counters.needsRequest());
    }

    static RefinementQueueRow rowOf(RefinementQueueEntry entry, LocalDate today) {
        RefinementQueueState state = stateOf(entry);
        boolean waiting = state != RefinementQueueState.NEEDS_REQUEST;
        boolean overdue = state == RefinementQueueState.WAITING_FOR_SUBMITTER
                && entry.dueDate() != null
                && entry.dueDate().isBefore(today);
        return new RefinementQueueRow(
                entry.briefId(),
                entry.title(),
                entry.code(),
                entry.category(),
                entry.stage(),
                waiting ? entry.cycle() : null,
                waiting ? entry.source() : null,
                waiting ? entry.dueDate() : null,
                state,
                overdue
        );
    }

    /** RESUBMITTED rows always carry a RESPONDED latest request (the query only returns those). */
    private static RefinementQueueState stateOf(RefinementQueueEntry entry) {
        if (entry.stage() == SubmissionStage.RESUBMITTED) {
            return RefinementQueueState.READY_TO_ACCEPT;
        }
        return entry.requestStatus() == RefinementStatus.OPEN
                ? RefinementQueueState.WAITING_FOR_SUBMITTER
                : RefinementQueueState.NEEDS_REQUEST;
    }
}
