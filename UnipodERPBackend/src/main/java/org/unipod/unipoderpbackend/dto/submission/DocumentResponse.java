package org.unipod.unipoderpbackend.dto.submission;

import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID submissionId,
        String name,
        DocumentStatus status,
        String fileReference,
        String createdBy,
        Instant createdAt
) {
    public static DocumentResponse from(SubmissionDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getSubmissionId(),
                document.getName(),
                document.getStatus(),
                document.getFileReference(),
                document.getCreatedBy(),
                document.getCreatedAt()
        );
    }
}
