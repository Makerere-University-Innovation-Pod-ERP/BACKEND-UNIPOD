package org.unipod.unipoderpbackend.submissions;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID submissionId,
        String name,
        DocumentStatus status,
        String fileReference,
        String contentType,
        Long fileSize,
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
                document.getContentType(),
                document.getFileSize(),
                document.getCreatedBy(),
                document.getCreatedAt()
        );
    }
}
