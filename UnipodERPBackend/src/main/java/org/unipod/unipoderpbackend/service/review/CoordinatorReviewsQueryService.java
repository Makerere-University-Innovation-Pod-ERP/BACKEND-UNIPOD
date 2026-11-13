package org.unipod.unipoderpbackend.service.review;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.review.CoordinatorReviewItem;
import org.unipod.unipoderpbackend.dto.review.CoordinatorReviewsResponse;
import org.unipod.unipoderpbackend.dto.review.FollowUpNoteResponse;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewFollowUpNoteRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.review.SubmittedReviewEntry;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

/**
 * The coordinator's view of submitted reviews, comments and follow-up notes for one round. Read-only
 * and not audited. A fixed number of queries regardless of the number of reviewers: brief, settings,
 * submitted reviews (one join), notes (one IN query), and two counts.
 */
@Service
public class CoordinatorReviewsQueryService {
    static final String COORDINATOR_ROLES = "hasAnyRole('INNOVATION_COORDINATOR', 'ADMINISTRATOR')";
    static final String ROUND_NOT_FOUND = "That review round does not exist for this brief.";

    private final SubmissionRepository submissions;
    private final ReviewSettingsRepository settings;
    private final ReviewAssignmentRepository assignments;
    private final ReviewRepository reviews;
    private final ReviewFollowUpNoteRepository notes;
    private final ReviewQuorumService quorumService;

    public CoordinatorReviewsQueryService(
            SubmissionRepository submissions,
            ReviewSettingsRepository settings,
            ReviewAssignmentRepository assignments,
            ReviewRepository reviews,
            ReviewFollowUpNoteRepository notes,
            ReviewQuorumService quorumService
    ) {
        this.submissions = submissions;
        this.settings = settings;
        this.assignments = assignments;
        this.reviews = reviews;
        this.notes = notes;
        this.quorumService = quorumService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(COORDINATOR_ROLES)
    public CoordinatorReviewsResponse reviews(UUID briefId, Integer round, Authentication authentication) {
        requireCoordinator(authentication);
        submissions.findVisibleById(briefId, SubmissionVisibility.scope(authentication), authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
        if (round != null && round < 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "round must be at least 1.");
        }

        ReviewSettings latest = settings.findTopByBriefIdOrderByRoundDesc(briefId).orElse(null);
        if (latest == null) {
            if (round != null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, ROUND_NOT_FOUND);
            }
            return new CoordinatorReviewsResponse(briefId, false, null, null, null, null, null,
                    new CoordinatorReviewsResponse.Quorum(0, 0, false), List.of(), 0);
        }
        ReviewSettings shown = round == null || round == latest.getRound()
                ? latest
                : settings.findByBriefIdAndRound(briefId, round)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ROUND_NOT_FOUND));

        List<SubmittedReviewEntry> submitted = reviews.findSubmittedForRound(briefId, shown.getRound());
        Map<Long, List<FollowUpNoteResponse>> notesByReview = submitted.isEmpty()
                ? Map.of()
                : notes.findByReviewIdInOrderByCreatedAtAscIdAsc(
                                submitted.stream().map(SubmittedReviewEntry::reviewId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(
                                note -> note.getReviewId(),
                                LinkedHashMap::new,
                                Collectors.mapping(FollowUpNoteResponse::from, Collectors.toList())));
        List<CoordinatorReviewItem> items = submitted.stream()
                .map(entry -> new CoordinatorReviewItem(
                        entry.reviewerId(),
                        entry.department(),
                        entry.position(),
                        entry.decision(),
                        entry.comment(),
                        entry.submittedAt(),
                        notesByReview.getOrDefault(entry.reviewId(), List.of())))
                .toList();

        ReviewQuorum quorum = quorumService.quorumFor(shown);
        int notSubmitted = Math.toIntExact(assignments.countByBriefIdAndRoundAndStatus(
                briefId, shown.getRound(), ReviewAssignmentStatus.NOT_STARTED));
        return new CoordinatorReviewsResponse(
                briefId,
                true,
                shown.getRound(),
                shown.getMode(),
                shown.getReviewsNeeded(),
                shown.getDueDate(),
                latest.getRound(),
                new CoordinatorReviewsResponse.Quorum(quorum.required(), quorum.completed(), quorum.met()),
                items,
                notSubmitted
        );
    }

    private static void requireCoordinator(Authentication authentication) {
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_INNOVATION_COORDINATOR".equals(authority.getAuthority())
                        || "ROLE_ADMINISTRATOR".equals(authority.getAuthority()));
        if (!allowed) {
            throw new AccessDeniedException("Only coordinators and administrators can read submitted reviews.");
        }
    }
}
