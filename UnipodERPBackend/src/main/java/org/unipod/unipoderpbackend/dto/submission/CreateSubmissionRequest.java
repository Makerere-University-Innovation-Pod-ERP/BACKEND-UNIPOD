package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.ServiceRequester;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

/**
 * The submitter-facing brief. Student fields apply to {@code STUDENT} briefs and
 * service fields apply to {@code SERVICE_REQUEST} briefs; general briefs leave
 * both groups empty.
 */
public record CreateSubmissionRequest(
        @NotNull SubmissionCategory category,
        @NotBlank @Size(max = 240) String title,
        @NotBlank @Size(max = 5000) String purpose,
        @NotBlank @Size(max = 5000) String problemAddressed,
        @Size(max = 255) String submitterId,
        @Size(max = 80) String studentNumber,
        @Size(max = 200) String programme,
        @Size(max = 255) String supervisor,
        RecommendationLetterStatus recommendationLetterStatus,
        @Email @Size(max = 254) String submitterEmail,
        @Size(max = 255) String submitterName,
        @Size(max = 40) String submitterPhone,
        @Size(max = 255) String serviceRequested,
        ServiceRequester serviceRequester,
        @Size(max = 255) String department,
        @Size(max = 5000) String expectedResult,
        @Size(max = 32) String acceptedRulesVersion
) {
    public CreateSubmissionRequest(
            SubmissionCategory category,
            String title,
            String purpose,
            String problemAddressed,
            String submitterId,
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus,
            String submitterEmail,
            String submitterName
    ) {
        this(category, title, purpose, problemAddressed, submitterId, studentNumber, programme,
                supervisor, recommendationLetterStatus, submitterEmail, submitterName,
                null, null, null, null, null, null);
    }

    public CreateSubmissionRequest(
            SubmissionCategory category,
            String title,
            String purpose,
            String problemAddressed,
            String submitterId,
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus,
            String submitterEmail
    ) {
        this(category, title, purpose, problemAddressed, submitterId, studentNumber, programme,
                supervisor, recommendationLetterStatus, submitterEmail, null);
    }

    public CreateSubmissionRequest(
            SubmissionCategory category,
            String title,
            String purpose,
            String problemAddressed,
            String submitterId,
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus
    ) {
        this(category, title, purpose, problemAddressed, submitterId, studentNumber, programme,
                supervisor, recommendationLetterStatus, null, null);
    }

    public CreateSubmissionRequest(
            SubmissionCategory category,
            String title,
            String purpose,
            String problemAddressed,
            String studentNumber,
            String programme,
            String supervisor,
            RecommendationLetterStatus recommendationLetterStatus
    ) {
        this(category, title, purpose, problemAddressed, null, studentNumber, programme,
                supervisor, recommendationLetterStatus, null, null);
    }
}
