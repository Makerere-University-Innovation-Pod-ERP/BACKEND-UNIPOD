package org.unipod.unipoderpbackend.dto.improvement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// caseVersion is the submission version from GET /cases/{id} at $.submission.version. Unknown properties
// are ignored so a client cannot supply acceptedBy, acceptedAt, versionNumber or other server-owned fields.
@JsonIgnoreProperties(ignoreUnknown = true)
public record AcceptRevisionRequest(Long caseVersion) {
}
