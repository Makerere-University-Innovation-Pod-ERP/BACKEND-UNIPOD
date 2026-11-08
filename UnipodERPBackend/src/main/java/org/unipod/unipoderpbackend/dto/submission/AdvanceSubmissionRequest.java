package org.unipod.unipoderpbackend.dto.submission;

import org.unipod.unipoderpbackend.model.submission.AdvanceTargetStage;

import jakarta.validation.constraints.NotNull;

public record AdvanceSubmissionRequest(@NotNull AdvanceTargetStage stage) {
}
