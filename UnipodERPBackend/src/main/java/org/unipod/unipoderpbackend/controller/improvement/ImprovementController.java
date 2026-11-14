package org.unipod.unipoderpbackend.controller.improvement;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.improvement.AskForChangesRequest;
import org.unipod.unipoderpbackend.dto.improvement.ImprovedVersionResponse;
import org.unipod.unipoderpbackend.dto.improvement.MyImprovementResponse;
import org.unipod.unipoderpbackend.dto.improvement.RefinementHistoryResponse;
import org.unipod.unipoderpbackend.dto.improvement.RefinementRequestResponse;
import org.unipod.unipoderpbackend.dto.improvement.SubmitImprovedVersionRequest;
import org.unipod.unipoderpbackend.service.improvement.ImprovementResponseService;
import org.unipod.unipoderpbackend.service.improvement.RefinementHistoryService;
import org.unipod.unipoderpbackend.service.improvement.RefinementRequestService;

/** Improvement endpoints (FR-010): staff ask for changes and read the history; the submitter answers. */
@RestController
@RequestMapping("/cases/{id}")
public class ImprovementController {
    private static final String STAFF_ROLES =
            "hasAnyRole('FACT_FINDING_OFFICER', 'INNOVATION_COORDINATOR', 'ADMINISTRATOR')";

    private static final String SUBMITTER_ONLY = "hasRole('SUBMITTER')";

    private final RefinementRequestService requests;
    private final RefinementHistoryService history;
    private final ImprovementResponseService responses;

    public ImprovementController(
            RefinementRequestService requests,
            RefinementHistoryService history,
            ImprovementResponseService responses
    ) {
        this.requests = requests;
        this.history = history;
        this.responses = responses;
    }

    @PostMapping("/refinement-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(STAFF_ROLES)
    public RefinementRequestResponse ask(
            @PathVariable UUID id,
            @RequestBody AskForChangesRequest request,
            Authentication authentication
    ) {
        return requests.ask(id, request, authentication);
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

    /** The submitter answers the open request with an improved version (through Waluube's reply). */
    @PostMapping("/improved-version")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SUBMITTER_ONLY)
    public ImprovedVersionResponse submitImprovedVersion(
            @PathVariable UUID id,
            @RequestBody SubmitImprovedVersionRequest request,
            Authentication authentication
    ) {
        return responses.submit(id, request, authentication);
    }
}
