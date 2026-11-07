package org.unipod.unipoderpbackend.submissions;

import java.time.Instant;
import java.util.UUID;

public record SubmitterSubmissionResponse(
        UUID id,
        String code,
        SubmissionCategory category,
        String title,
        String purpose,
        String problemAddressed,
        String studentNumber,
        String programme,
        String supervisor,
        RecommendationLetterStatus recommendationLetterStatus,
        SubmissionStage stage,
        Instant receivedAt,
        Instant forwardedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static SubmitterSubmissionResponse from(Submission submission) {
        return new SubmitterSubmissionResponse(
                submission.getId(),
                submission.getCode(),
                submission.getCategory(),
                submission.getTitle(),
                submission.getPurpose(),
                submission.getProblemAddressed(),
                submission.getStudentNumber(),
                submission.getProgramme(),
                submission.getSupervisor(),
                submission.getRecommendationLetterStatus(),
                submission.getStage(),
                submission.getReceivedAt(),
                submission.getForwardedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
