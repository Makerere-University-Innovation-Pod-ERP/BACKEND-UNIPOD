package org.unipod.unipoderpbackend.dto.submission;

import java.util.List;

public record CaseFileResponse(
        SubmissionResponse submission,
        List<DocumentResponse> documents,
        List<CaseStageHistoryResponse> stageHistory
) {
}
