package org.unipod.unipoderpbackend.repository.review;

import java.time.Instant;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;

/** A SUBMITTED assignment joined with its review, as read by {@link ReviewRepository#findSubmittedForRound}. */
public record SubmittedReviewEntry(
        Long assignmentId,
        String reviewerId,
        String department,
        int position,
        Long reviewId,
        ReviewDecision decision,
        String comment,
        Instant submittedAt
) {
}
