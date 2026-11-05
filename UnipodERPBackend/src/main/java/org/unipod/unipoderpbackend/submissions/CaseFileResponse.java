package org.unipod.unipoderpbackend.submissions;

import java.util.List;

public record CaseFileResponse(
        SubmissionResponse submission,
        List<DocumentResponse> documents,
        List<CaseStageHistoryResponse> stageHistory
) {
}
