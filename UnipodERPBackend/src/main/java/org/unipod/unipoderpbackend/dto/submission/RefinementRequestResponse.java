package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;

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
        List<String> requestedChanges
) {
    public static RefinementRequestResponse from(RefinementRequest request, List<String> requestedChanges) {
        return new RefinementRequestResponse(
                request.getId(),
                request.getBriefId(),
                request.getCycle(),
                request.getSource(),
                request.getGuidanceNote(),
                request.getDueDate(),
                request.getStatus(),
                request.getRequestedBy(),
                request.getRequestedAt(),
                request.getRespondedAt(),
                List.copyOf(requestedChanges)
        );
    }
}
