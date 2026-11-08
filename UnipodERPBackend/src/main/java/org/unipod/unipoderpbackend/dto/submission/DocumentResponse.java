package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;

public record DocumentResponse(
        UUID id,
        UUID submissionId,
        String name,
        DocumentStatus status,
        String fileReference,
        String contentType,
        Long fileSize,
        String originalFilename,
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
                document.getOriginalFilename(),
                document.getCreatedBy(),
                document.getCreatedAt()
        );
    }
}
