package org.unipod.unipoderpbackend.dto.factfinding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/**
 * The fact-finding officer's "needs more information" outcome. {@code version} is the fact-finding check's version
 * (GET /cases/{id}/fact-finding); {@code caseVersion} is the submission version ($.submission.version of
 * GET /cases/{id}). The guidance note, due date and requested changes are written for the submitter and become the
 * refinement request. Unknown properties are ignored so a client cannot supply result, completedAt, officerId, round,
 * source or cycle. Field rules are checked in FactFindingNeedsInformationService and reported as 422 naming the field.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NeedsMoreInformationRequest(
        Long version,
        Long caseVersion,
        String guidanceNote,
        LocalDate dueDate,
        List<String> requestedChanges
) {
}
