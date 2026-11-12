package org.unipod.unipoderpbackend.dto.submission;

import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.DocumentType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 255) String name,
        DocumentType documentType,
        @NotNull DocumentStatus status,
        @Size(max = 256)
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "must be an opaque file reference")
        String fileReference,
        @Min(1) Long version
) {
    public CreateDocumentRequest {
        if (documentType == null) {
            documentType = DocumentType.OTHER;
        }
    }

    public CreateDocumentRequest(String name, DocumentType documentType, DocumentStatus status, String fileReference) {
        this(name, documentType, status, fileReference, null);
    }

    public CreateDocumentRequest(String name, DocumentStatus status, String fileReference) {
        this(name, DocumentType.OTHER, status, fileReference, null);
    }
}
