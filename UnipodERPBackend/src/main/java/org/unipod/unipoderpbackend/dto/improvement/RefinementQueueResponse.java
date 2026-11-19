package org.unipod.unipoderpbackend.dto.improvement;

import org.unipod.unipoderpbackend.dto.PageResponse;

/** The staff refinement queue. Counters are added in a later step. */
public record RefinementQueueResponse(PageResponse<RefinementQueueRow> rows) {
}
