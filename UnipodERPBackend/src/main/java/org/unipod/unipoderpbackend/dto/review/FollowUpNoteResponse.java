package org.unipod.unipoderpbackend.dto.review;

import java.time.Instant;
import org.unipod.unipoderpbackend.model.review.ReviewFollowUpNote;

/** A follow-up note the calling reviewer added to their own submitted review. */
public record FollowUpNoteResponse(Long id, String note, String createdBy, Instant createdAt) {
    public static FollowUpNoteResponse from(ReviewFollowUpNote note) {
        return new FollowUpNoteResponse(note.getId(), note.getNote(), note.getCreatedBy(), note.getCreatedAt());
    }
}
