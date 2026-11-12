package org.unipod.unipoderpbackend.dto.training;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterParticipantRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String regNumber,
        @NotBlank @Size(max = 200) String department,
        @Email @Size(max = 254) String contactEmail) {
}
