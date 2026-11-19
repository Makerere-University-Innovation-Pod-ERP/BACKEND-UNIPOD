package org.unipod.unipoderpbackend.dto.improvement;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/**
 * One brief in the staff refinement queue. {@code trackingReference} is the brief's code and is {@code null} until
 * the brief has one. {@code cycle}, {@code source}, {@code dueDate} and {@code requestedChanges} describe the request
 * the row is waiting on; they are {@code null} (an empty list for the changes) for NEEDS_REQUEST. {@code overdue} is
 * true only for WAITING_FOR_SUBMITTER rows due before today (Africa/Kampala). {@code versionsSoFar} is the latest
 * revision's version number, or 1 (the original brief) when there is none; {@code latestRevisionId} is that
 * revision's id, the one a screen accepts, or {@code null}.
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
        boolean overdue,
        List<RequestedChangeItem> requestedChanges,
        int versionsSoFar,
        Long latestRevisionId
) {
}
