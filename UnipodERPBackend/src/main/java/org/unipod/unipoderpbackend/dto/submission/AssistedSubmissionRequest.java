package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.ServiceRequester;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

/**
 * A walk-in brief captured by reception on behalf of a submitter. The submitter
 * account is created (or reused) from {@code submitterEmail} and the brief is
 * saved as a draft that the submitter can complete and submit.
 */
public record AssistedSubmissionRequest(
        @NotBlank @Email @Size(max = 254) String submitterEmail,
        @NotNull SubmissionCategory category,
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 5000) String purpose,
        @NotBlank @Size(max = 5000) String problemAddressed,
        @Size(max = 80) String studentNumber,
        @Size(max = 200) String programme,
        @Size(max = 255) String supervisor,
        RecommendationLetterStatus recommendationLetterStatus,
        @Size(max = 40) String submitterPhone,
        @Size(max = 255) String serviceRequested,
        ServiceRequester serviceRequester,
        @Size(max = 255) String department,
        @Size(max = 5000) String expectedResult,
        @Size(max = 32) String acceptedRulesVersion
) {
    public AssistedSubmissionRequest(
            String submitterEmail,
            SubmissionCategory category,
            String title,
            String purpose,
            String problemAddressed,
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus
    ) {
        this(submitterEmail, category, title, purpose, problemAddressed, studentNumber,
                programme, supervisor, recommendationLetterStatus, null, null, null,
                null, null, null);
    }
}
