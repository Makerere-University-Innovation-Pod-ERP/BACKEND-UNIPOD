package org.unipod.unipoderpbackend.controller.review;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.review.AssignReviewerRequest;
import org.unipod.unipoderpbackend.dto.review.CoordinatorReviewsResponse;
import org.unipod.unipoderpbackend.dto.review.ReviewersPanelResponse;
import org.unipod.unipoderpbackend.dto.review.SaveReviewSettingsRequest;
import org.unipod.unipoderpbackend.service.review.CoordinatorReviewsQueryService;
import org.unipod.unipoderpbackend.service.review.ReviewCommandService;
import org.unipod.unipoderpbackend.service.review.ReviewPanelQueryService;
import org.unipod.unipoderpbackend.service.review.ReviewQuorum;

@RestController
@RequestMapping("/cases/{id}")
public class ReviewController {
    private static final String REVIEW_PANEL_READ_ROLES =
            "hasAnyRole('INNOVATION_COORDINATOR', 'ADMINISTRATOR')";
    private static final String REVIEW_PANEL_WRITE_ROLES =
            "hasAnyRole('INNOVATION_COORDINATOR', 'ADMINISTRATOR')";

    private final ReviewPanelQueryService panels;
    private final ReviewCommandService commands;
    private final CoordinatorReviewsQueryService submittedReviews;

    public ReviewController(
            ReviewPanelQueryService panels,
            ReviewCommandService commands,
            CoordinatorReviewsQueryService submittedReviews
    ) {
        this.panels = panels;
        this.commands = commands;
        this.submittedReviews = submittedReviews;
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

    /** Submitted reviews with comments and follow-up notes; POST on the same path is the reviewer's submit. */
    @GetMapping("/reviews")
    @PreAuthorize(REVIEW_PANEL_READ_ROLES)
    public CoordinatorReviewsResponse submittedReviews(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer round,
            Authentication authentication
    ) {
        return submittedReviews.reviews(id, round, authentication);
    }

    @PutMapping("/review-settings")
    @PreAuthorize(REVIEW_PANEL_WRITE_ROLES)
    public ReviewersPanelResponse saveSettings(
            @PathVariable UUID id,
            @RequestBody SaveReviewSettingsRequest request,
            Authentication authentication
    ) {
        return commands.saveSettings(id, request, authentication);
    }

    @PostMapping("/reviewers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(REVIEW_PANEL_WRITE_ROLES)
    public ReviewersPanelResponse assignReviewer(
            @PathVariable UUID id,
            @RequestBody AssignReviewerRequest request,
            Authentication authentication
    ) {
        return commands.assignReviewer(id, request, authentication);
    }

    @DeleteMapping("/reviewers/{reviewerId}")
    @PreAuthorize(REVIEW_PANEL_WRITE_ROLES)
    public ReviewersPanelResponse removeReviewer(
            @PathVariable UUID id,
            @PathVariable String reviewerId,
            Authentication authentication
    ) {
        return commands.removeReviewer(id, reviewerId, authentication);
    }
}
