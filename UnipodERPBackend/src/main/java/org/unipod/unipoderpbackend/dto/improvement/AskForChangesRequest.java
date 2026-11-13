package org.unipod.unipoderpbackend.dto.improvement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

// Unknown properties are ignored so a client cannot supply id, cycle, source, status, requestedBy,
// requestedAt or other server-owned fields. Field rules are checked in RefinementRequestService and
// reported as 422 naming the field. caseVersion is the submission version (GET /cases/{id} at
// $.submission.version); it is required only when the brief has to be moved to IMPROVEMENT_REQUESTED.
@JsonIgnoreProperties(ignoreUnknown = true)
public record AskForChangesRequest(
        String guidanceNote,
        LocalDate dueDate,
        List<String> requestedChanges,
        Long caseVersion
) {
}
