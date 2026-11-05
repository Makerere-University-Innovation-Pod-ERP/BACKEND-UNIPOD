package org.unipod.unipoderpbackend.submissions;

import java.time.Instant;
import java.util.UUID;

public record CaseStageHistoryResponse(
        UUID id,
        SubmissionStage fromStage,
        SubmissionStage toStage,
        String changedBy,
        Instant changedAt
) {
    public static CaseStageHistoryResponse from(CaseStageHistory entry) {
        return new CaseStageHistoryResponse(
                entry.getId(),
                entry.getFromStage(),
                entry.getToStage(),
                entry.getChangedBy(),
                entry.getChangedAt()
        );
    }
}
