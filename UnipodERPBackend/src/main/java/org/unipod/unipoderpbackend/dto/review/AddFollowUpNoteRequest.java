package org.unipod.unipoderpbackend.dto.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Unknown properties are ignored so a client cannot supply id, reviewId, createdBy, createdAt or other
// server-owned fields. Field rules are checked in ReviewSubmissionService and reported as 422 naming the field.
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddFollowUpNoteRequest(String note) {
}
