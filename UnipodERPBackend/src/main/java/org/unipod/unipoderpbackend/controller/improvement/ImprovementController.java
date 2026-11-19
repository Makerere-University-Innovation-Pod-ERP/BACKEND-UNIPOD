package org.unipod.unipoderpbackend.controller.improvement;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.improvement.AcceptRevisionRequest;
import org.unipod.unipoderpbackend.dto.improvement.AcceptRevisionResponse;
import org.unipod.unipoderpbackend.dto.improvement.MyImprovementResponse;
import org.unipod.unipoderpbackend.dto.improvement.RefinementHistoryResponse;
import org.unipod.unipoderpbackend.service.improvement.ImprovementResponseService;
import org.unipod.unipoderpbackend.service.improvement.RefinementHistoryService;
import org.unipod.unipoderpbackend.service.improvement.RevisionAcceptanceService;

/**
 * Improvement endpoints (FR-010) with no counterpart on main: staff read the history, the submitter reads their
 * own view, and the coordinator accepts an improved version. Asking for changes and sending the improved version
 * are main's CaseFileController endpoints.
 */
@RestController
@RequestMapping("/cases/{id}")
public class ImprovementController {
    private static final String STAFF_ROLES =
            "hasAnyRole('FACT_FINDING_OFFICER', 'INNOVATION_COORDINATOR', 'ADMINISTRATOR')";

    private static final String SUBMITTER_ONLY = "hasRole('SUBMITTER')";
    private static final String OPERATOR_ROLES = "hasAnyRole('INNOVATION_COORDINATOR', 'ADMINISTRATOR')";

    private final RefinementHistoryService history;
    private final ImprovementResponseService responses;
    private final RevisionAcceptanceService acceptance;

    public ImprovementController(
            RefinementHistoryService history,
            ImprovementResponseService responses,
            RevisionAcceptanceService acceptance
    ) {
        this.history = history;
        this.responses = responses;
        this.acceptance = acceptance;
    }

    @GetMapping("/refinement-history")
    @PreAuthorize(STAFF_ROLES)
    public RefinementHistoryResponse history(@PathVariable UUID id, Authentication authentication) {
        return history.history(id, authentication);
    }

    /** The submitter's own view: what is asked of them and their earlier answers. */
    @GetMapping("/my-improvement")
    @PreAuthorize(SUBMITTER_ONLY)
    public MyImprovementResponse myImprovement(@PathVariable UUID id, Authentication authentication) {
        return responses.myImprovement(id, authentication);
    }

    /** The coordinator accepts the latest improved version and returns the brief to review (existing round). */
    @PostMapping("/revisions/{revisionId}/accept")
    @PreAuthorize(OPERATOR_ROLES)
    public AcceptRevisionResponse acceptRevision(
            @PathVariable UUID id,
            @PathVariable Long revisionId,
            @RequestBody AcceptRevisionRequest request,
            Authentication authentication
    ) {
        return acceptance.accept(id, revisionId, request, authentication);
    }
}
