package org.unipod.unipoderpbackend.submissions;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SectorRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 320) String notificationEmail,
        @NotNull Boolean active
) {
}
