package org.unipod.unipoderpbackend.submissions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SectorMemberRequest(
        @NotBlank @Size(max = 255) String subject
) {
}
