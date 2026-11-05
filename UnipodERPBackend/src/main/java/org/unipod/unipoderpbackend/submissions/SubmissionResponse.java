package org.unipod.unipoderpbackend.submissions;

import java.time.Instant;
import java.util.UUID;

public record SubmissionResponse(
        UUID id,
        String code,
        SubmissionCategory category,
        String title,
        String purpose,
        String problemAddressed,
        String registeredBy,
        String receivingOfficer,
        String studentNumber,
        String programme,
        String supervisor,
        RecommendationLetterStatus recommendationLetterStatus,
        UUID responsibleSectorId,
        SubmissionStage stage,
        Instant receivedAt,
        Instant forwardedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static SubmissionResponse from(Submission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getCode(),
                submission.getCategory(),
                submission.getTitle(),
                submission.getPurpose(),
                submission.getProblemAddressed(),
                submission.getRegisteredBy(),
                submission.getReceivingOfficer(),
                submission.getStudentNumber(),
                submission.getProgramme(),
                submission.getSupervisor(),
                submission.getRecommendationLetterStatus(),
                submission.getResponsibleSectorId(),
                submission.getStage(),
                submission.getReceivedAt(),
                submission.getForwardedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
