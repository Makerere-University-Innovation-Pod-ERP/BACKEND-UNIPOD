package org.unipod.unipoderpbackend.dto.review;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewMode;

/**
 * The coordinator's reviewers panel for the latest review round of a brief.
 * Review comments are deliberately not part of this response.
 */
public record ReviewersPanelResponse(
        UUID briefId,
        boolean configured,
        Integer round,
        ReviewMode mode,
        Integer reviewsNeeded,
        LocalDate dueDate,
        Quorum quorum,
        List<Reviewer> reviewers
) {
    public record Quorum(int required, int completed, boolean met) {
    }

    public record Reviewer(
            String reviewerId,
            String department,
            int position,
            ReviewAssignmentStatus status,
            String assignedBy,
            ReviewDecision decision,
            Instant submittedAt
    ) {
    }
}
