package org.unipod.unipoderpbackend.submissions;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ForwardSubmissionRequest(@NotNull UUID sectorId) {
}
