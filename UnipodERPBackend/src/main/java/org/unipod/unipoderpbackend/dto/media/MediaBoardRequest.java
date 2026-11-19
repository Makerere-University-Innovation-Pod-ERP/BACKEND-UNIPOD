package org.unipod.unipoderpbackend.dto.media;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.unipod.unipoderpbackend.model.media.MediaBoardStatus;
import org.unipod.unipoderpbackend.model.operations.MediaRequest;

/**
 * One card on the media board, exactly the frontend's {@code mediaRequestSchema}. The schema declares
 * {@code date} and {@code description} optional but not nullable, so they are left out when empty,
 * while {@code studio} may be null and {@code assignee} is never null (D-040).
 */
public record MediaBoardRequest(
        String id,
        String title,
        String studio,
        String assignee,
        MediaBoardStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime date,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description) {

    /** @param studioName the name of the request's studio facility, or null when none is set */
    public static MediaBoardRequest from(MediaRequest request, String studioName, ZoneId zone) {
        return new MediaBoardRequest(
                request.getId().toString(),
                request.getTitle(),
                studioName,
                request.getAssignee() == null ? "" : request.getAssignee(),
                MediaBoardStatus.shown(request.getStatus()).orElseThrow(),
                request.getScheduledAt() == null ? null : request.getScheduledAt().atZone(zone).toOffsetDateTime(),
                request.getDescription());
    }
}
