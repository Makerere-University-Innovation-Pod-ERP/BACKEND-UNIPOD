package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.ServiceRequester;
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
        String submitterName,
        String submitterEmail,
        String submitterPhone,
        String receivingOfficer,
        String assignedReviewerSubject,
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
        String acceptedRulesVersion,
        UUID responsibleSectorId,
        String responsibleSectorName,
        SubmissionStage stage,
        Instant receivedAt,
        Instant submittedAt,
        Instant forwardedAt,
        String forwardNote,
        String forwardedBy,
        Instant confirmedAt,
        String rejectionReason,
        String informationRequestedNote,
        Instant informationRequestedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
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
                submission.getSubmitterName(),
                submission.getSubmitterEmail(),
                submission.getSubmitterPhone(),
                submission.getReceivingOfficer(),
                submission.getAssignedReviewerSubject(),
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
                submission.getAcceptedRulesVersion(),
                submission.getResponsibleSectorId(),
                submission.getResponsibleSectorName(),
                submission.getStage(),
                submission.getReceivedAt(),
                submission.getSubmittedAt(),
                submission.getForwardedAt(),
                submission.getForwardNote(),
                submission.getForwardedBy(),
                submission.getConfirmedAt(),
                submission.getRejectionReason(),
                submission.getInformationRequestedNote(),
                submission.getInformationRequestedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt(),
                submission.getVersion()
        );
    }
}
