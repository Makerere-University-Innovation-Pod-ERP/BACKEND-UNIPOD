package org.unipod.unipoderpbackend.dto.improvement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;

/**
 * The submitter's improved version. {@code whatChanged} and {@code requestVersion} (the open request's
 * version from GET my-improvement) are required; every brief field is optional and null means unchanged.
 * The brief fields are exactly those of Waluube's ReplySubmissionRequest and are applied by his reply.
 * Unknown properties are ignored so a client cannot supply versionNumber, requestId, submittedBy or
 * submittedAt.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitImprovedVersionRequest(
        String whatChanged,
        Long requestVersion,
        String title,
        String purpose,
        String problemAddressed,
        String studentNumber,
        String programme,
        String supervisor,
        RecommendationLetterStatus recommendationLetterStatus,
        String submitterPhone,
        String serviceRequested,
        String department,
        String expectedResult,
        Instant preferredAt
) {
}
