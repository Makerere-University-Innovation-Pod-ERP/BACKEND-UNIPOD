package org.unipod.unipoderpbackend.dto.submission;

import java.util.List;

public record MissingDocsQueueResponse(
        List<MissingDocsQueueItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long overdueCount
) {
}
