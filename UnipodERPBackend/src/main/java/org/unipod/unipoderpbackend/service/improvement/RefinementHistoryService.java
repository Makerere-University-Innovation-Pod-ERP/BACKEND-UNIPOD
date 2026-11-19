package org.unipod.unipoderpbackend.service.improvement;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.improvement.RefinementHistoryResponse;
import org.unipod.unipoderpbackend.dto.improvement.RequestedChangeItem;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

/**
 * Staff view of every request for changes on a brief. Read-only, not audited, and a fixed number of
 * queries: the brief, its requests, all their changes (one IN query) and its revisions.
 */
@Service
public class RefinementHistoryService {
    static final String HISTORY_ROLES = ImprovementRoles.STAFF_ROLES;

    private final SubmissionRepository submissions;
    private final RefinementRequestRepository requests;
    private final RefinementRequestedChangeRepository changes;
    private final CaseRevisionRepository revisions;

    public RefinementHistoryService(
            SubmissionRepository submissions,
            RefinementRequestRepository requests,
            RefinementRequestedChangeRepository changes,
            CaseRevisionRepository revisions
    ) {
        this.submissions = submissions;
        this.requests = requests;
        this.changes = changes;
        this.revisions = revisions;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(HISTORY_ROLES)
    public RefinementHistoryResponse history(UUID briefId, Authentication authentication) {
        boolean allowed = ImprovementRoles.has(authentication, "ROLE_FACT_FINDING_OFFICER")
                || ImprovementRoles.has(authentication, "ROLE_INNOVATION_COORDINATOR")
                || ImprovementRoles.has(authentication, "ROLE_ADMINISTRATOR");
        if (!allowed) {
            throw new AccessDeniedException("Only fact-finding officers, coordinators and administrators can read "
                    + "the refinement history.");
        }
        submissions.findVisibleById(briefId, SubmissionVisibility.scope(authentication), authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));

        List<RefinementRequest> asked = requests.findByBriefIdOrderByCycleAsc(briefId);
        if (asked.isEmpty()) {
            return new RefinementHistoryResponse(briefId, List.of());
        }
        Map<Long, List<RequestedChangeItem>> changesByRequest = changes
                .findByRequestIdInOrderByRequestIdAscPositionAsc(asked.stream().map(RefinementRequest::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        change -> change.getRequestId(),
                        Collectors.mapping(RequestedChangeItem::from, Collectors.toList())));
        Map<Long, CaseRevision> revisionByRequest = revisions.findByBriefIdOrderByVersionNumberAsc(briefId).stream()
                .collect(Collectors.toMap(CaseRevision::getRequestId, Function.identity()));

        return new RefinementHistoryResponse(briefId, asked.stream()
                .map(request -> {
                    CaseRevision revision = revisionByRequest.get(request.getId());
                    return new RefinementHistoryResponse.Request(
                            request.getId(),
                            request.getCycle(),
                            request.getSource(),
                            request.getGuidanceNote(),
                            request.getDueDate(),
                            request.getStatus(),
                            request.getRequestedBy(),
                            request.getRequestedAt(),
                            request.getRespondedAt(),
                            changesByRequest.getOrDefault(request.getId(), List.of()),
                            revision == null ? null : new RefinementHistoryResponse.Revision(
                                    revision.getVersionNumber(),
                                    revision.getWhatChanged(),
                                    revision.getSubmittedBy(),
                                    revision.getSubmittedAt(),
                                    revision.getId()));
                })
                .toList());
    }
}
