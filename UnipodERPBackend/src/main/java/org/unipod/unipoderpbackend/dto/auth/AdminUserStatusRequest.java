package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotNull;

public record AdminUserStatusRequest(
        @NotNull Boolean active,
        @NotNull Long version
) {
}
