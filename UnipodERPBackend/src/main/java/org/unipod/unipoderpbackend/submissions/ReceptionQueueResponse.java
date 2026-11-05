package org.unipod.unipoderpbackend.submissions;

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
