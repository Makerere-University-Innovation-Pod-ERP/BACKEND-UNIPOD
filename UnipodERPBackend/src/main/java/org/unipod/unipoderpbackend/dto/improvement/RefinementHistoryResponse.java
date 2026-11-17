package org.unipod.unipoderpbackend.dto.improvement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;

/** Every request for changes on a brief, oldest cycle first, each with the revision that answered it. */
public record RefinementHistoryResponse(UUID briefId, List<Request> requests) {
    public record Request(
            Long id,
            int cycle,
            RefinementSource source,
            String guidanceNote,
            LocalDate dueDate,
            RefinementStatus status,
            String requestedBy,
            Instant requestedAt,
            Instant respondedAt,
            List<RequestedChangeItem> requestedChanges,
            Revision revision
    ) {
    }

    public record Revision(int versionNumber, String whatChanged, String submittedBy, Instant submittedAt, Long id) {
    }
}
