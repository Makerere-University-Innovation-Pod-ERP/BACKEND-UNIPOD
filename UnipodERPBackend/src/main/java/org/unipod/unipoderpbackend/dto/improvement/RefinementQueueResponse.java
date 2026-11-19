package org.unipod.unipoderpbackend.dto.improvement;

import org.unipod.unipoderpbackend.dto.PageResponse;

/**
 * The staff refinement queue. The four counters cover every row of the queue, not only this page, and classify
 * rows exactly as {@link RefinementQueueRow#state()} and {@link RefinementQueueRow#overdue()} do.
 */
public record RefinementQueueResponse(
        PageResponse<RefinementQueueRow> rows,
        long waitingForSubmitter,
        long overdue,
        long readyToAccept,
        long needsRequest
) {
}
