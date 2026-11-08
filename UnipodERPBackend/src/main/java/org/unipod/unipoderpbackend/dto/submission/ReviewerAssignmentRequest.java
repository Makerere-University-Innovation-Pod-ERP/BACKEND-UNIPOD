package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.constraints.Size;

public record ReviewerAssignmentRequest(
        @Size(max = 255) String subject
) {
}
