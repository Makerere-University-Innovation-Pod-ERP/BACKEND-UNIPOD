package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

/**
 * One brief waiting on the submitter in the reception "missing documents"
 * queue. The overdue flag is derived from the earliest open item deadline and
 * the last reminder is the most recent reminder sent across the open items.
 */
public record MissingDocsQueueItem(
        UUID submissionId,
        String code,
        String title,
        SubmissionCategory category,
        String submitterName,
        String submitterEmail,
        Instant submittedAt,
        Instant dueAt,
        Instant lastRemindedAt,
        boolean overdue,
        List<MissingItemResponse> missingItems
) {
}
