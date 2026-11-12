package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A request for missing information. {@code note} remains the single-line form
 * used by older clients; {@code items} allows reception to list each missing
 * line with its own reason, due date and submitter-facing message.
 */
public record RequestInformationRequest(
        @Size(max = 2000) String note,
        @Size(min = 1) @Valid List<@NotNull MissingItemRequest> items,
        @Size(max = 255) String reason
) {
    public RequestInformationRequest(String note, List<MissingItemRequest> items) {
        this(note, items, null);
    }

    public RequestInformationRequest(String note) {
        this(note, null, null);
    }
}
