package org.unipod.unipoderpbackend.dto.improvement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/** A request for changes, as returned to the staff member who made it. */
public record RefinementRequestResponse(
        Long id,
        UUID briefId,
        int cycle,
        RefinementSource source,
        String guidanceNote,
        LocalDate dueDate,
        RefinementStatus status,
        String requestedBy,
        Instant requestedAt,
        Instant respondedAt,
        List<RequestedChangeItem> requestedChanges,
        Long version,
        SubmissionStage briefStage
) {
}
