package org.unipod.unipoderpbackend.dto.review;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.review.ReviewMode;

/**
 * The submitted reviews of one review round, for the coordinator and administrator. Reviews appear as
 * soon as they are submitted. Reviewers who have not submitted appear only in {@code notSubmittedCount}.
 * {@code latestRound} is the highest round with settings, so earlier rounds can be offered.
 */
public record CoordinatorReviewsResponse(
        UUID briefId,
        boolean configured,
        Integer round,
        ReviewMode mode,
        Integer reviewsNeeded,
        LocalDate dueDate,
        Integer latestRound,
        Quorum quorum,
        List<CoordinatorReviewItem> reviews,
        int notSubmittedCount
) {
    public record Quorum(int required, int completed, boolean met) {
    }
}
