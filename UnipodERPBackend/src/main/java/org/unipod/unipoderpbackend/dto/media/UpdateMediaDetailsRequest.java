package org.unipod.unipoderpbackend.dto.media;

import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sets the board fields of a request. An omitted or null field is left as it is; a blank
 * {@code assignee} or {@code description} clears it. The studio and the date can be replaced, not cleared.
 */
public record UpdateMediaDetailsRequest(
        UUID studioId,
        @Size(max = 200) String assignee,
        OffsetDateTime date,
        @Size(max = 2000) String description) {
}
