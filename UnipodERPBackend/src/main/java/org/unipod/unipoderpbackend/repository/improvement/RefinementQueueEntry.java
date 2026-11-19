package org.unipod.unipoderpbackend.repository.improvement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/**
 * One brief in the staff refinement queue, as read by {@link RefinementRequestRepository#findQueue}: the brief
 * and its latest refinement request (highest cycle). The request fields are all {@code null} for a brief in
 * IMPROVEMENT_REQUESTED that has no recorded request.
 */
public record RefinementQueueEntry(
        UUID briefId,
        String title,
        String code,
        SubmissionCategory category,
        SubmissionStage stage,
        Instant updatedAt,
        Long requestId,
        Integer cycle,
        RefinementSource source,
        LocalDate dueDate,
        RefinementStatus requestStatus
) {
}
