package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.submission.DocumentType;

public record MissingItemRequest(
        @NotBlank @Size(max = 255) String label,
        @Size(max = 2000) String note,
        DocumentType documentType
) {
}
