package org.unipod.unipoderpbackend.controller.factfinding;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.factfinding.CompleteFactFindingRequest;
import org.unipod.unipoderpbackend.dto.factfinding.FactFindingResponse;
import org.unipod.unipoderpbackend.dto.factfinding.NeedsMoreInformationRequest;
import org.unipod.unipoderpbackend.dto.factfinding.SaveFactFindingRequest;
import org.unipod.unipoderpbackend.service.factfinding.FactFindingCommandService;
import org.unipod.unipoderpbackend.service.factfinding.FactFindingNeedsInformationService;
import org.unipod.unipoderpbackend.service.factfinding.FactFindingQueryService;

@RestController
@RequestMapping("/cases/{id}")
public class FactFindingController {
    private static final String FACT_FINDING_READ_ROLES =
            "hasAnyRole('FACT_FINDING_OFFICER', 'INNOVATION_COORDINATOR', 'ADMINISTRATOR')";
    private static final String FACT_FINDING_OFFICER_ONLY = "hasRole('FACT_FINDING_OFFICER')";

    private final FactFindingQueryService queries;
    private final FactFindingCommandService commands;
    private final FactFindingNeedsInformationService needsInformation;

    public FactFindingController(
            FactFindingQueryService queries,
            FactFindingCommandService commands,
            FactFindingNeedsInformationService needsInformation
    ) {
        this.queries = queries;
        this.commands = commands;
        this.needsInformation = needsInformation;
    }

    @GetMapping("/fact-finding")
    @PreAuthorize(FACT_FINDING_READ_ROLES)
    public FactFindingResponse get(@PathVariable UUID id, Authentication authentication) {
        return queries.getForBrief(id, authentication);
    }

    @PutMapping("/fact-finding")
    public FactFindingResponse save(
            @PathVariable UUID id,
            @RequestBody SaveFactFindingRequest request,
            Authentication authentication
    ) {
        return commands.saveDraft(id, request, authentication);
    }

    @PostMapping("/fact-finding/complete")
    public FactFindingResponse complete(
            @PathVariable UUID id,
            @RequestBody CompleteFactFindingRequest request,
            Authentication authentication
    ) {
        return commands.complete(id, request, authentication);
    }

    /**
     * The officer's "needs more information" outcome: completes the check with result NEEDS_MORE_INFORMATION and
     * asks the submitter for changes (main's ask), in one transaction.
     */
    @PostMapping("/fact-finding/needs-information")
    @PreAuthorize(FACT_FINDING_OFFICER_ONLY)
    public FactFindingResponse needsInformation(
            @PathVariable UUID id,
            @RequestBody NeedsMoreInformationRequest request,
            Authentication authentication
    ) {
        return needsInformation.needsMoreInformation(id, request, authentication);
    }
}
