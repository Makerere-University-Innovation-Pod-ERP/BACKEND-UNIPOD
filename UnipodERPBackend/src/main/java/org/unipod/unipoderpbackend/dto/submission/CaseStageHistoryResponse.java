package org.unipod.unipoderpbackend.dto.submission;

import org.unipod.unipoderpbackend.model.submission.CaseStageHistory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

import java.time.Instant;
import java.util.UUID;

public record CaseStageHistoryResponse(
        UUID id,
        SubmissionStage fromStage,
        SubmissionStage toStage,
        String changedBy,
        String note,
        Instant changedAt
) {
    public static CaseStageHistoryResponse from(CaseStageHistory entry) {
        return new CaseStageHistoryResponse(
                entry.getId(),
                entry.getFromStage(),
                entry.getToStage(),
                entry.getChangedBy(),
                entry.getNote(),
                entry.getChangedAt()
        );
    }
}
