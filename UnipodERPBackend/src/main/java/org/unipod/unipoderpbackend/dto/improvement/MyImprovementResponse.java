package org.unipod.unipoderpbackend.dto.improvement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/**
 * The submitter's own view of improvement requests on their brief. Deliberately carries no staff identity
 * (no requestedBy, no source) and nothing from fact-finding or reviews: only the guidance written for the
 * submitter, the items asked for, the due date and the submitter's own revisions.
 */
public record MyImprovementResponse(
        UUID briefId,
        SubmissionStage briefStage,
        OpenRequest openRequest,
        List<PreviousRequest> previousRequests
) {
    public record OpenRequest(
            Long id,
            int cycle,
            String guidanceNote,
            LocalDate dueDate,
            List<RequestedChangeItem> requestedChanges,
            Long version
    ) {
    }

    public record PreviousRequest(
            int cycle,
            String guidanceNote,
            LocalDate dueDate,
            List<RequestedChangeItem> requestedChanges,
            Instant respondedAt,
            Revision revision
    ) {
    }

    public record Revision(int versionNumber, String whatChanged, Instant submittedAt) {
    }
}
