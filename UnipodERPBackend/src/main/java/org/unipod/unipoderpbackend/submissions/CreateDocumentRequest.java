package org.unipod.unipoderpbackend.submissions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull DocumentStatus status,
        @Size(max = 256)
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "must be an opaque file reference")
        String fileReference
) {
}
