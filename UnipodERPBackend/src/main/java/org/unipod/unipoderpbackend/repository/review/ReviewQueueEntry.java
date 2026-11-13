package org.unipod.unipoderpbackend.repository.review;

import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/** One row of a reviewer's queue, as read by {@link ReviewAssignmentRepository#findQueue}. */
public record ReviewQueueEntry(
        Long assignmentId,
        UUID briefId,
        String title,
        String code,
        SubmissionCategory category,
        int round,
        int position,
        LocalDate dueDate,
        ReviewAssignmentStatus status,
        SubmissionStage stage
) {
}
