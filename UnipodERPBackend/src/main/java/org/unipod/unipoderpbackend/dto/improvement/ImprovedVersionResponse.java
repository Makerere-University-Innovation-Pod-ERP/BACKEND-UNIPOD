package org.unipod.unipoderpbackend.dto.improvement;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

public record ImprovedVersionResponse(
        UUID briefId,
        int versionNumber,
        String whatChanged,
        Instant submittedAt,
        int requestCycle,
        SubmissionStage briefStage
) {
}
