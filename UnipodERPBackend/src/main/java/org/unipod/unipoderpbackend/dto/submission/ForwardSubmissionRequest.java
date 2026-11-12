package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ForwardSubmissionRequest(
        @NotNull UUID sectorId,
        @Size(max = 2000) String note
) {
    public ForwardSubmissionRequest(UUID sectorId) {
        this(sectorId, null);
    }
}
