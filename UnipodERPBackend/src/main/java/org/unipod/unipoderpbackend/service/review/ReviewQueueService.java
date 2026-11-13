package org.unipod.unipoderpbackend.service.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.review.ReviewQueueResponse;
import org.unipod.unipoderpbackend.dto.review.ReviewQueueRow;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewQueueEntry;

/**
 * The calling reviewer's own queue. Read-only: three queries per request (the page, the total and
 * the waiting count), independent of the page size. Access is decided by the review module's own
 * assignments, not by the submission visibility rule.
 */
@Service
public class ReviewQueueService {
    static final String REVIEWER_ONLY = "hasRole('REVIEWER')";

    private final ReviewAssignmentRepository assignments;

    public ReviewQueueService(ReviewAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(REVIEWER_ONLY)
    public ReviewQueueResponse queue(Pageable pageable, Authentication authentication) {
        boolean reviewer = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_REVIEWER".equals(authority.getAuthority()));
        if (!reviewer) {
            throw new AccessDeniedException("Only reviewers have a review queue.");
        }
        String caller = authentication.getName();
        Page<ReviewQueueRow> rows = assignments.findQueue(caller, pageable).map(ReviewQueueService::rowOf);
        long waiting = assignments.countQueueWaiting(caller);
        return new ReviewQueueResponse(rows.getTotalElements(), waiting, PageResponse.from(rows));
    }

    /** Every row the query returns is already released, so canSubmit depends only on status and stage. */
    private static ReviewQueueRow rowOf(ReviewQueueEntry entry) {
        boolean canSubmit = entry.status() == ReviewAssignmentStatus.NOT_STARTED
                && entry.stage() == SubmissionStage.UNDER_REVIEW;
        return new ReviewQueueRow(
                entry.briefId(),
                entry.title(),
                entry.code(),
                entry.category(),
                entry.round(),
                entry.position(),
                entry.dueDate(),
                entry.status(),
                entry.stage(),
                canSubmit
        );
    }
}
