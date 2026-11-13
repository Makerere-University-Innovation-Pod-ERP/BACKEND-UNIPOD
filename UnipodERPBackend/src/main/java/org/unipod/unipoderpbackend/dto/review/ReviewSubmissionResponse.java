package org.unipod.unipoderpbackend.dto.review;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;

/**
 * The caller's own submitted review. {@code quorum} is the latest round's quorum computed after
 * this submission was saved.
 */
public record ReviewSubmissionResponse(
        UUID briefId,
        int round,
        String reviewerId,
        ReviewDecision decision,
        String comment,
        Instant submittedAt,
        int position,
        Quorum quorum
) {
    public record Quorum(int required, int completed, boolean met) {
    }
}
