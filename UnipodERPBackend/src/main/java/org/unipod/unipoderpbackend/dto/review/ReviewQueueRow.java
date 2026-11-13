package org.unipod.unipoderpbackend.dto.review;

import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/**
 * One brief in the calling reviewer's queue. {@code trackingReference} is the brief's code and is
 * {@code null} until the brief has one; {@code dueDate} comes from the round's review settings.
 */
public record ReviewQueueRow(
        UUID briefId,
        String title,
        String trackingReference,
        SubmissionCategory briefType,
        int round,
        int position,
        LocalDate dueDate,
        ReviewAssignmentStatus status,
        SubmissionStage stage,
        boolean canSubmit
) {
}
