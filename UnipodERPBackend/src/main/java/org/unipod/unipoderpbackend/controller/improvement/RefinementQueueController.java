package org.unipod.unipoderpbackend.controller.improvement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.improvement.RefinementQueueResponse;
import org.unipod.unipoderpbackend.service.improvement.RefinementQueueService;

/** The staff queue of briefs being improved. Paging follows the other list endpoints (default 25, max 100). */
@RestController
@RequestMapping("/queues")
@Validated
public class RefinementQueueController {
    private static final String QUEUE_ROLES =
            "hasAnyRole('FACT_FINDING_OFFICER', 'INNOVATION_COORDINATOR', 'ADMINISTRATOR')";

    private final RefinementQueueService queues;

    public RefinementQueueController(RefinementQueueService queues) {
        this.queues = queues;
    }

    @GetMapping("/refinement")
    @PreAuthorize(QUEUE_ROLES)
    public RefinementQueueResponse refinement(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return queues.queue(PageRequest.of(page, size), authentication);
    }
}
