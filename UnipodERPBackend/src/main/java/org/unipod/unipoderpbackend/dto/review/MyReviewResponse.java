package org.unipod.unipoderpbackend.dto.review;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewMode;

/**
 * The calling reviewer's own assignment in the latest review round. It never contains other
 * reviewers' data or how many reviewers exist. {@code decision}, {@code comment} and
 * {@code submittedAt} are {@code null} until the caller has submitted.
 */
public record MyReviewResponse(
        UUID briefId,
        int round,
        int position,
        String department,
        ReviewAssignmentStatus status,
        Long assignmentVersion,
        ReviewMode mode,
        LocalDate dueDate,
        ReviewDecision decision,
        String comment,
        Instant submittedAt
) {
}
