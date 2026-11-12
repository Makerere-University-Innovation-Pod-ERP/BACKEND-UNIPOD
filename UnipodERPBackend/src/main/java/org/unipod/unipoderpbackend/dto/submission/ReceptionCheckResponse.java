package org.unipod.unipoderpbackend.dto.submission;

import java.util.List;
import java.util.UUID;

public record ReceptionCheckResponse(
        UUID submissionId,
        boolean complete,
        int checkedCount,
        int totalCount,
        List<ReceptionCheckItemResponse> items
) {
}
