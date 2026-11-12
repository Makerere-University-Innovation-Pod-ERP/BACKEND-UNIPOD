package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.Size;

public record ResolveMissingItemRequest(
        @Size(max = 2000) String note
) {
}
