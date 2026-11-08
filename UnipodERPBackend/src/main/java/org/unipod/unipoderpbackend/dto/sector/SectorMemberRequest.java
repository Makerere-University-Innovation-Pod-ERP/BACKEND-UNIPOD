package org.unipod.unipoderpbackend.dto.sector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SectorMemberRequest(
        @NotBlank @Size(max = 255) String subject
) {
}
