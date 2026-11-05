package org.unipod.unipoderpbackend.submissions;

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
