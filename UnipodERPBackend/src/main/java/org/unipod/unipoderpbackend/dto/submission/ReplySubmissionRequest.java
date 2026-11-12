package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;

/**
 * The submitter's response to an information request. Every brief field is
 * optional so a submitter can reply with only the missing detail.
 */
public record ReplySubmissionRequest(
        @Size(max = 240) String title,
        @Size(max = 5000) String purpose,
        @Size(max = 5000) String problemAddressed,
        @Size(max = 80) String studentNumber,
        @Size(max = 200) String programme,
        @Size(max = 255) String supervisor,
        RecommendationLetterStatus recommendationLetterStatus,
        @Size(max = 40) String submitterPhone,
        @Size(max = 255) String serviceRequested,
        @Size(max = 255) String department,
        @Size(max = 5000) String expectedResult,
        @Size(max = 2000) String note
) {
}
