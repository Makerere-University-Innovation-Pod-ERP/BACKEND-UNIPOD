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
 * {@code version} is the latest review_settings row's version, to send back when updating settings.
 */
public record ReviewersPanelResponse(
        UUID briefId,
        boolean configured,
        Integer round,
        ReviewMode mode,
        Integer reviewsNeeded,
        LocalDate dueDate,
        Long version,
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
