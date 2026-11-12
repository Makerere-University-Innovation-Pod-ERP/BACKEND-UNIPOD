package org.unipod.unipoderpbackend.dto.submission;

import java.util.List;
import java.util.UUID;

public record MissingItemsResponse(
        UUID submissionId,
        long openCount,
        List<MissingItemResponse> items
) {
}
