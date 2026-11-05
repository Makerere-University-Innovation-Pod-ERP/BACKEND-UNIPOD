package org.unipod.unipoderpbackend.submissions;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SectorRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank
        @Email
        @Size(max = 320)
        @Pattern(
                regexp = "(?i)^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
                message = "must be a valid routable email address"
        )
        String notificationEmail,
        @NotNull Boolean active,
        Long version
) {
    public SectorRequest(String name, String notificationEmail, Boolean active) {
        this(name, notificationEmail, active, null);
    }
}
