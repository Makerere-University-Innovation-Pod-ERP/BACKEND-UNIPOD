package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.ServiceRequester;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

/**
 * A partially completed brief saved from the submitter's draft editor. The
 * category is required when the draft is created and may be omitted on later
 * autosaves, which only send the field that changed. The tracking reference is
 * allocated when the draft is submitted, not when it is saved.
 */
public record DraftSubmissionRequest(
        SubmissionCategory category,
        @Size(max = 240) String title,
        @Size(max = 5000) String purpose,
        @Size(max = 5000) String problemAddressed,
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
        Instant preferredAt,
        @Size(max = 32) String acceptedRulesVersion
) {
}
