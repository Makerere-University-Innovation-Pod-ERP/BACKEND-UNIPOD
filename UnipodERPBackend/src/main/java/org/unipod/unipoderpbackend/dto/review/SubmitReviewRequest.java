package org.unipod.unipoderpbackend.dto.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;

// Unknown properties are ignored so a client cannot supply reviewerId, round, submittedAt, status
// or other server-owned fields. Field rules are checked in ReviewSubmissionService and reported
// as 422 naming the field.
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitReviewRequest(
        ReviewDecision decision,
        String comment,
        Long assignmentVersion
) {
}
