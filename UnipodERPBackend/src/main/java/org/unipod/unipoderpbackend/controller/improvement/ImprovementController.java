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
import org.unipod.unipoderpbackend.dto.improvement.RefinementHistoryResponse;
import org.unipod.unipoderpbackend.dto.improvement.RefinementRequestResponse;
import org.unipod.unipoderpbackend.service.improvement.RefinementHistoryService;
import org.unipod.unipoderpbackend.service.improvement.RefinementRequestService;

/** Staff endpoints for asking a submitter to improve a brief (FR-010). Submitters have no access here. */
@RestController
@RequestMapping("/cases/{id}")
public class ImprovementController {
    private static final String STAFF_ROLES =
            "hasAnyRole('FACT_FINDING_OFFICER', 'INNOVATION_COORDINATOR', 'ADMINISTRATOR')";

    private final RefinementRequestService requests;
    private final RefinementHistoryService history;

    public ImprovementController(RefinementRequestService requests, RefinementHistoryService history) {
        this.requests = requests;
        this.history = history;
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
}
