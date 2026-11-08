package org.unipod.unipoderpbackend.dto.submission;

import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSubmissionRequest(
        @NotNull SubmissionCategory category,
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 5000) String purpose,
        @NotBlank @Size(max = 5000) String problemAddressed,
        @Size(max = 80) String studentNumber,
        @Size(max = 200) String programme,
        @Size(max = 255) String supervisor,
        RecommendationLetterStatus recommendationLetterStatus
) {
}
