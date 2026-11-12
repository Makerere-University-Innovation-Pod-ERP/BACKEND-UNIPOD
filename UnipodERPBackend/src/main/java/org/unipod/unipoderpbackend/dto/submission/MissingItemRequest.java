package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.unipod.unipoderpbackend.model.submission.DocumentType;

/**
 * One requested line in a "more information" request. A {@code documentType}
 * makes it a document request; otherwise it is an information request.
 */
public record MissingItemRequest(
        @NotBlank @Size(max = 255) String label,
        @Size(max = 2000) String note,
        DocumentType documentType,
        @Size(max = 255) String reason,
        Instant dueAt,
        @Size(max = 2000) String message
) {
    public MissingItemRequest(String label, String note, DocumentType documentType) {
        this(label, note, documentType, null, null, null);
    }
}
