package org.unipod.unipoderpbackend.controller.review;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.review.ReviewersPanelResponse;
import org.unipod.unipoderpbackend.service.review.ReviewPanelQueryService;
import org.unipod.unipoderpbackend.service.review.ReviewQuorum;

@RestController
@RequestMapping("/cases/{id}")
public class ReviewController {
    private static final String REVIEW_PANEL_READ_ROLES =
            "hasAnyRole('INNOVATION_COORDINATOR', 'ADMINISTRATOR')";

    private final ReviewPanelQueryService panels;

    public ReviewController(ReviewPanelQueryService panels) {
        this.panels = panels;
    }

    @GetMapping("/review-quorum")
    @PreAuthorize(REVIEW_PANEL_READ_ROLES)
    public ReviewQuorum quorum(@PathVariable UUID id, Authentication authentication) {
        return panels.quorum(id, authentication);
    }

    @GetMapping("/reviewers")
    @PreAuthorize(REVIEW_PANEL_READ_ROLES)
    public ReviewersPanelResponse reviewers(@PathVariable UUID id, Authentication authentication) {
        return panels.panel(id, authentication);
    }
}
