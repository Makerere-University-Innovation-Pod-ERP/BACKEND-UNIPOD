package org.unipod.unipoderpbackend.repository.factfinding;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/**
 * One brief waiting for its first fact-finding check, as read by {@link FactFindingQueueRepository#findQueue}: the
 * brief and its round-1 check if one has been saved. The check fields are all {@code null} when no check exists yet.
 */
public record FactFindingQueueEntry(
        UUID briefId,
        String title,
        String code,
        SubmissionCategory category,
        SubmissionStage stage,
        Instant forwardedAt,
        Instant updatedAt,
        Long checkId,
        String checkOfficerId,
        Instant checkUpdatedAt
) {
}
