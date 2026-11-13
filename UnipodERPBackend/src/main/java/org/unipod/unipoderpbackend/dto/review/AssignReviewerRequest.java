package org.unipod.unipoderpbackend.dto.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Only the named server-owned fields are ignored; any other unknown property is still rejected.
// Field rules are checked in ReviewCommandService and reported as 422 naming the field.
@JsonIgnoreProperties({"round", "position", "status", "assignedBy"})
public record AssignReviewerRequest(
        String reviewerId,
        String department
) {
}
