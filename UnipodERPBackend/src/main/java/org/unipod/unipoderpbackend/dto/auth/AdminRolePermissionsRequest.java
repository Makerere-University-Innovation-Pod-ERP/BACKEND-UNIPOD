package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AdminRolePermissionsRequest(
        @NotNull Set<@NotNull String> permissions,
        @NotNull @Min(0) Long version
) {
}
