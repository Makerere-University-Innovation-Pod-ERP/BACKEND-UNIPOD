package org.unipod.unipoderpbackend.dto.factfinding;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

public record FactFindingResponse(
        UUID briefId,
        String briefTitle,
        String trackingReference,
        SubmissionCategory briefType,
        int round,
        String officerId,
        Boolean titleClear,
        String titleNote,
        Boolean purposeClear,
        String purposeNote,
        Boolean evidenceEnough,
        String evidenceNote,
        Boolean relevantToHub,
        String relevantNote,
        String missingItems,
        FactFindingResult result,
        Instant completedAt,
        FactFindingState state,
        Long version
) {
}
