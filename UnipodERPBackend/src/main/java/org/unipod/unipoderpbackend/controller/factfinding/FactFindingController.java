package org.unipod.unipoderpbackend.controller.factfinding;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.factfinding.FactFindingResponse;
import org.unipod.unipoderpbackend.service.factfinding.FactFindingQueryService;

@RestController
@RequestMapping("/cases/{id}")
public class FactFindingController {
    private static final String FACT_FINDING_READ_ROLES =
            "hasAnyRole('FACT_FINDING_OFFICER', 'INNOVATION_COORDINATOR', 'ADMINISTRATOR')";

    private final FactFindingQueryService queries;

    public FactFindingController(FactFindingQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/fact-finding")
    @PreAuthorize(FACT_FINDING_READ_ROLES)
    public FactFindingResponse get(@PathVariable UUID id, Authentication authentication) {
        return queries.getForBrief(id, authentication);
    }
}
