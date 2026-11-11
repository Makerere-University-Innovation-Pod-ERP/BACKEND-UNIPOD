package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

public record SubmitterSubmissionResponse(
        UUID id,
        String code,
        String submitterName,
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
        String informationRequestedNote,
        Instant informationRequestedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static SubmitterSubmissionResponse from(Submission submission) {
        return new SubmitterSubmissionResponse(
                submission.getId(),
                submission.getCode(),
                submission.getSubmitterName(),
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
                submission.getInformationRequestedNote(),
                submission.getInformationRequestedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
