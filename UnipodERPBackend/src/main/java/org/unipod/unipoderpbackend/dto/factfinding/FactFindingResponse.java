package org.unipod.unipoderpbackend.dto.factfinding;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.submission.Submission;
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
    public static FactFindingResponse from(Submission brief, FactFindingCheck check) {
        return new FactFindingResponse(
                brief.getId(),
                brief.getTitle(),
                brief.getCode(),
                brief.getCategory(),
                check.getRound(),
                check.getOfficerId(),
                check.getTitleClear(),
                check.getTitleNote(),
                check.getPurposeClear(),
                check.getPurposeNote(),
                check.getEvidenceEnough(),
                check.getEvidenceNote(),
                check.getRelevantToHub(),
                check.getRelevantNote(),
                check.getMissingItems(),
                check.getResult(),
                check.getCompletedAt(),
                check.getCompletedAt() == null ? FactFindingState.DRAFT : FactFindingState.COMPLETED,
                check.getVersion()
        );
    }

    public static FactFindingResponse notStarted(Submission brief) {
        return new FactFindingResponse(
                brief.getId(),
                brief.getTitle(),
                brief.getCode(),
                brief.getCategory(),
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FactFindingState.NOT_STARTED,
                null
        );
    }
}
