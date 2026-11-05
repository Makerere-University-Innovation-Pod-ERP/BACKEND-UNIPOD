package org.unipod.unipoderpbackend.submissions;

import jakarta.validation.constraints.NotNull;

public record AdvanceSubmissionRequest(@NotNull AdvanceTargetStage stage) {
}
