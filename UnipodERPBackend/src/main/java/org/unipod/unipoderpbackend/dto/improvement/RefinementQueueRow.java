package org.unipod.unipoderpbackend.dto.improvement;

import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/**
 * One brief in the staff refinement queue. {@code trackingReference} is the brief's code and is {@code null} until
 * the brief has one. {@code cycle}, {@code source} and {@code dueDate} describe the request the row is waiting on
 * and are {@code null} for NEEDS_REQUEST. {@code overdue} is true only for WAITING_FOR_SUBMITTER rows due before
 * today (Africa/Kampala).
 */
public record RefinementQueueRow(
        UUID briefId,
        String title,
        String trackingReference,
        SubmissionCategory briefType,
        SubmissionStage stage,
        Integer cycle,
        RefinementSource source,
        LocalDate dueDate,
        RefinementQueueState state,
        boolean overdue
) {
}
