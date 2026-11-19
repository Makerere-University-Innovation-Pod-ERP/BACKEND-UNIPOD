package org.unipod.unipoderpbackend.service.improvement;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import org.unipod.unipoderpbackend.dto.improvement.RequestedChangeItem;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.LatestRevision;
import org.unipod.unipoderpbackend.repository.improvement.RefinementQueueCounters;
import org.unipod.unipoderpbackend.repository.improvement.RefinementQueueEntry;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;

/**
 * The staff queue of briefs being improved. Read-only and not audited, with a fixed number of queries whatever the
 * page holds: the page, its count when needed, the counters, the page's requested changes (one IN query) and the
 * page's latest revisions (one IN query); a query whose id set is empty is skipped. "Today" is the hub's date (Africa/Kampala) from the shared clock; the same date decides
 * the query's overdue-first order, each row's overdue flag and the overdue counter.
 */
@Service
public class RefinementQueueService {
    static final String QUEUE_ROLES = ImprovementRoles.STAFF_ROLES;
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    private final RefinementRequestRepository requests;
    private final RefinementRequestedChangeRepository changes;
    private final CaseRevisionRepository revisions;
    private final Clock clock;

    public RefinementQueueService(
            RefinementRequestRepository requests,
            RefinementRequestedChangeRepository changes,
            CaseRevisionRepository revisions,
            Clock clock
    ) {
        this.requests = requests;
        this.changes = changes;
        this.revisions = revisions;
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
        Page<RefinementQueueEntry> entries = requests.findQueue(today, pageable);

        List<Long> requestIds = entries.getContent().stream()
                .map(RefinementQueueEntry::requestId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, List<RequestedChangeItem>> changesByRequest = requestIds.isEmpty()
                ? Map.of()
                : changes.findByRequestIdInOrderByRequestIdAscPositionAsc(requestIds).stream()
                        .collect(Collectors.groupingBy(change -> change.getRequestId(),
                                Collectors.mapping(RequestedChangeItem::from, Collectors.toList())));
        Map<UUID, LatestRevision> latestByBrief = revisions.findLatestForBriefs(
                        entries.getContent().stream().map(RefinementQueueEntry::briefId).toList())
                .stream()
                .collect(Collectors.toMap(LatestRevision::briefId, Function.identity()));

        Page<RefinementQueueRow> rows = entries.map(entry -> rowOf(entry, today,
                entry.requestId() == null ? List.of() : changesByRequest.getOrDefault(entry.requestId(), List.of()),
                latestByBrief.get(entry.briefId())));
        RefinementQueueCounters counters = requests.countQueue(today);
        return new RefinementQueueResponse(PageResponse.from(rows), counters.waitingForSubmitter(),
                counters.overdue(), counters.readyToAccept(), counters.needsRequest());
    }

    /** A row for a brief with no requested changes loaded and no revision (the original brief only). */
    static RefinementQueueRow rowOf(RefinementQueueEntry entry, LocalDate today) {
        return rowOf(entry, today, List.of(), null);
    }

    /**
     * {@code requestChanges} are the changes of the entry's (latest) request; they are shown only when the row is
     * waiting on that request. {@code latest} is the brief's highest revision, or {@code null}.
     */
    static RefinementQueueRow rowOf(RefinementQueueEntry entry, LocalDate today,
            List<RequestedChangeItem> requestChanges, LatestRevision latest) {
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
                overdue,
                waiting ? List.copyOf(requestChanges) : List.of(),
                latest == null ? 1 : latest.versionNumber(),
                latest == null ? null : latest.revisionId()
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
