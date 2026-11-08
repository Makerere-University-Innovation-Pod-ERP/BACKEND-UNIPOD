package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ForwardSubmissionRequest(@NotNull UUID sectorId) {
}
