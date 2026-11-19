package org.unipod.unipoderpbackend.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https://.+")
        String endpoint,
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[A-Za-z0-9_-]+={0,2}$")
        String p256dh,
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[A-Za-z0-9_-]+={0,2}$")
        String auth
) {
}
