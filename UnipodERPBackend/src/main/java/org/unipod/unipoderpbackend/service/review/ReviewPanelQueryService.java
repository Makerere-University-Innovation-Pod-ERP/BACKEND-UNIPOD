package org.unipod.unipoderpbackend.service.review;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.review.ReviewersPanelResponse;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

@Service
public class ReviewPanelQueryService {
    private final SubmissionRepository submissions;
    private final ReviewSettingsRepository settings;
    private final ReviewAssignmentRepository assignments;
    private final ReviewRepository reviews;
    private final ReviewQuorumService quorumService;

    public ReviewPanelQueryService(
            SubmissionRepository submissions,
            ReviewSettingsRepository settings,
            ReviewAssignmentRepository assignments,
            ReviewRepository reviews,
            ReviewQuorumService quorumService
    ) {
        this.submissions = submissions;
        this.settings = settings;
        this.assignments = assignments;
        this.reviews = reviews;
        this.quorumService = quorumService;
    }

    @Transactional(readOnly = true)
    public ReviewQuorum quorum(UUID briefId, Authentication authentication) {
        requireVisibleBrief(briefId, authentication);
        return quorumService.quorum(briefId);
    }

    @Transactional(readOnly = true)
    public ReviewersPanelResponse panel(UUID briefId, Authentication authentication) {
        requireVisibleBrief(briefId, authentication);
        return panelFor(briefId);
    }

    /** Builds the panel without a visibility check; callers must already have loaded the brief. */
    ReviewersPanelResponse panelFor(UUID briefId) {
        return settings.findTopByBriefIdOrderByRoundDesc(briefId)
                .map(latest -> configuredPanel(briefId, latest))
                .orElseGet(() -> new ReviewersPanelResponse(
                        briefId, false, null, null, null, null, null,
                        quorumOf(ReviewQuorum.notConfigured()), List.of()));
    }

    private ReviewersPanelResponse configuredPanel(UUID briefId, ReviewSettings latest) {
        List<ReviewersPanelResponse.Reviewer> reviewers = assignments
                .findByBriefIdAndRoundOrderByPositionAsc(briefId, latest.getRound())
                .stream()
                .map(this::reviewerOf)
                .toList();
        return new ReviewersPanelResponse(
                briefId,
                true,
                latest.getRound(),
                latest.getMode(),
                latest.getReviewsNeeded(),
                latest.getDueDate(),
                latest.getVersion(),
                quorumOf(quorumService.quorumFor(latest)),
                reviewers
        );
    }

    private ReviewersPanelResponse.Reviewer reviewerOf(ReviewAssignment assignment) {
        Review review = assignment.getStatus() == ReviewAssignmentStatus.SUBMITTED
                ? reviews.findByAssignmentId(assignment.getId()).orElse(null)
                : null;
        return new ReviewersPanelResponse.Reviewer(
                assignment.getReviewerId(),
                assignment.getDepartment(),
                assignment.getPosition(),
                assignment.getStatus(),
                assignment.getAssignedBy(),
                review == null ? null : review.getDecision(),
                review == null ? null : review.getSubmittedAt()
        );
    }

    private static ReviewersPanelResponse.Quorum quorumOf(ReviewQuorum quorum) {
        return new ReviewersPanelResponse.Quorum(quorum.required(), quorum.completed(), quorum.met());
    }

    private void requireVisibleBrief(UUID briefId, Authentication authentication) {
        String scope = SubmissionVisibility.scope(authentication);
        submissions.findVisibleById(briefId, scope, authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
    }
}
