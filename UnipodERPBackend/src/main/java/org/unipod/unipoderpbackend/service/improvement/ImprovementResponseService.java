package org.unipod.unipoderpbackend.service.improvement;

import java.util.ArrayList;
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
import org.unipod.unipoderpbackend.dto.improvement.MyImprovementResponse;
import org.unipod.unipoderpbackend.dto.improvement.RequestedChangeItem;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

/**
 * The submitter's read-only view of improvement requests on their own brief. Answering with an improved
 * version is main's POST /cases/{id}/improved-version (SubmissionService#submitImprovedVersion).
 */
@Service
public class ImprovementResponseService {
    static final String SUBMITTER_ONLY = "hasRole('SUBMITTER')";

    private final SubmissionRepository submissions;
    private final RefinementRequestRepository requests;
    private final RefinementRequestedChangeRepository changes;
    private final CaseRevisionRepository revisions;

    public ImprovementResponseService(
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
    @PreAuthorize(SUBMITTER_ONLY)
    public MyImprovementResponse myImprovement(UUID briefId, Authentication authentication) {
        requireSubmitter(authentication);
        Submission brief = submissions.findVisibleById(
                        briefId, SubmissionVisibility.scope(authentication), authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));

        List<RefinementRequest> asked = requests.findByBriefIdOrderByCycleAsc(briefId);
        if (asked.isEmpty()) {
            return new MyImprovementResponse(briefId, brief.getStage(), null, List.of());
        }
        Map<Long, List<RequestedChangeItem>> changesByRequest = changes
                .findByRequestIdInOrderByRequestIdAscPositionAsc(asked.stream().map(RefinementRequest::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(change -> change.getRequestId(),
                        Collectors.mapping(RequestedChangeItem::from, Collectors.toList())));
        Map<Long, CaseRevision> revisionByRequest = revisions.findByBriefIdOrderByVersionNumberAsc(briefId).stream()
                .collect(Collectors.toMap(CaseRevision::getRequestId, Function.identity()));

        MyImprovementResponse.OpenRequest open = null;
        List<MyImprovementResponse.PreviousRequest> previous = new ArrayList<>();
        for (RefinementRequest request : asked) {
            List<RequestedChangeItem> items = changesByRequest.getOrDefault(request.getId(), List.of());
            if (request.getStatus() == RefinementStatus.OPEN) {
                open = new MyImprovementResponse.OpenRequest(request.getId(), request.getCycle(),
                        request.getGuidanceNote(), request.getDueDate(), items, request.getVersion());
            } else {
                CaseRevision revision = revisionByRequest.get(request.getId());
                previous.add(new MyImprovementResponse.PreviousRequest(request.getCycle(), request.getGuidanceNote(),
                        request.getDueDate(), items, request.getRespondedAt(),
                        revision == null ? null : new MyImprovementResponse.Revision(
                                revision.getVersionNumber(), revision.getWhatChanged(), revision.getSubmittedAt(),
                                revision.getId())));
            }
        }
        return new MyImprovementResponse(briefId, brief.getStage(), open, previous);
    }

    private static void requireSubmitter(Authentication authentication) {
        if (!ImprovementRoles.has(authentication, "ROLE_SUBMITTER")) {
            throw new AccessDeniedException("Only the submitter can see or answer improvement requests here.");
        }
    }
}
