package org.unipod.unipoderpbackend.submissions;

import jakarta.validation.constraints.Size;

public record ReviewerAssignmentRequest(
        @Size(max = 255) String subject
) {
}
