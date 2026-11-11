package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.ServiceRequester;
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
        String serviceRequested,
        ServiceRequester serviceRequester,
        String department,
        String expectedResult,
        String projectCode,
        int briefVersion,
        SubmissionStage stage,
        Instant receivedAt,
        Instant submittedAt,
        Instant forwardedAt,
        Instant confirmedAt,
        String rejectionReason,
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
                submission.getServiceRequested(),
                submission.getServiceRequester(),
                submission.getDepartment(),
                submission.getExpectedResult(),
                submission.getProjectCode(),
                submission.getBriefVersion(),
                submission.getStage(),
                submission.getReceivedAt(),
                submission.getSubmittedAt(),
                submission.getForwardedAt(),
                submission.getConfirmedAt(),
                submission.getRejectionReason(),
                submission.getInformationRequestedNote(),
                submission.getInformationRequestedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
