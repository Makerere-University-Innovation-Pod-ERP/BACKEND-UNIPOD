package org.unipod.unipoderpbackend.dto.sector;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.validation.HttpsUrl;

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
        Long version,
        @Size(max = 500)
        @HttpsUrl(message = "must be an absolute https:// invite link")
        String whatsappGroupUrl
) {
    public SectorRequest(String name, String notificationEmail, Boolean active) {
        this(name, notificationEmail, active, null, null);
    }

    public SectorRequest(String name, String notificationEmail, Boolean active, Long version) {
        this(name, notificationEmail, active, version, null);
    }
}
