package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

public record SubmissionResponse(
        UUID id,
        String code,
        SubmissionCategory category,
        String title,
        String purpose,
        String problemAddressed,
        String registeredBy,
        String submitterId,
        String receivingOfficer,
        String assignedReviewerSubject,
        String studentNumber,
        String programme,
        String supervisor,
        RecommendationLetterStatus recommendationLetterStatus,
        UUID responsibleSectorId,
        String responsibleSectorName,
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
                submission.getSubmitterId(),
                submission.getReceivingOfficer(),
                submission.getAssignedReviewerSubject(),
                submission.getStudentNumber(),
                submission.getProgramme(),
                submission.getSupervisor(),
                submission.getRecommendationLetterStatus(),
                submission.getResponsibleSectorId(),
                submission.getResponsibleSectorName(),
                submission.getStage(),
                submission.getReceivedAt(),
                submission.getForwardedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
