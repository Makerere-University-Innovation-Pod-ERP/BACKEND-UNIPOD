package org.unipod.unipoderpbackend.dto.review;

import java.time.Instant;
import java.util.List;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;

/** One submitted review with its comment and follow-up notes. Staff-only (coordinator, administrator). */
public record CoordinatorReviewItem(
        String reviewerId,
        String department,
        int position,
        ReviewDecision decision,
        String comment,
        Instant submittedAt,
        List<FollowUpNoteResponse> followUpNotes
) {
}
