package org.unipod.unipoderpbackend.dto.reception;

import java.util.List;
import org.unipod.unipoderpbackend.dto.submission.MissingItemResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;

public record MissingDocumentQueueResponse(
        List<Item> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        long awaitingInformationCount,
        long overdueItems
) {
    public record Item(SubmissionResponse submission, List<MissingItemResponse> openItems) {
    }
}
