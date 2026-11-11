package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A request for missing information. {@code note} remains the single-line form
 * used by older clients; {@code items} allows reception to list each missing
 * line with its own note.
 */
public record RequestInformationRequest(
        @Size(max = 2000) String note,
        @Valid List<MissingItemRequest> items
) {
    public RequestInformationRequest(String note) {
        this(note, null);
    }
}
