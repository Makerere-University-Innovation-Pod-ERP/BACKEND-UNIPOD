package org.unipod.unipoderpbackend.dto.improvement;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/** {@code briefStage} is the stored stage after the transition (IN_REVIEW). */
public record AcceptRevisionResponse(
        UUID briefId,
        Long revisionId,
        int versionNumber,
        SubmissionStage briefStage,
        String acceptedBy,
        Instant acceptedAt
) {
}
