package org.unipod.unipoderpbackend.controller.review;

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
import org.unipod.unipoderpbackend.dto.review.ReviewQueueResponse;
import org.unipod.unipoderpbackend.service.review.ReviewQueueService;

/** The calling reviewer's own review queue. Paging follows the other list endpoints (default 25, max 100). */
@RestController
@RequestMapping("/queues")
@Validated
public class ReviewQueueController {
    private static final String REVIEWER_ONLY = "hasRole('REVIEWER')";

    private final ReviewQueueService queues;

    public ReviewQueueController(ReviewQueueService queues) {
        this.queues = queues;
    }

    @GetMapping("/reviews")
    @PreAuthorize(REVIEWER_ONLY)
    public ReviewQueueResponse reviews(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return queues.queue(PageRequest.of(page, size), authentication);
    }
}
