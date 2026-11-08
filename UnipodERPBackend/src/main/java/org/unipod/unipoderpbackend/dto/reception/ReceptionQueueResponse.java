package org.unipod.unipoderpbackend.dto.reception;

import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;

import java.util.List;

public record ReceptionQueueResponse(
        List<SubmissionResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long inQueueCount,
        long awaitingActionCount,
        long recentlyUpdatedCount
) {
}
