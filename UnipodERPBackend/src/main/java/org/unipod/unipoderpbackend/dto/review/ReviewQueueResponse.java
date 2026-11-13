package org.unipod.unipoderpbackend.dto.review;

import org.unipod.unipoderpbackend.dto.PageResponse;

/**
 * The calling reviewer's queue. Both counters cover all of the caller's rows, not only this page:
 * {@code assignedToMe} is the number of rows, {@code waitingForMyReview} those with canSubmit true.
 */
public record ReviewQueueResponse(
        long assignedToMe,
        long waitingForMyReview,
        PageResponse<ReviewQueueRow> rows
) {
}
