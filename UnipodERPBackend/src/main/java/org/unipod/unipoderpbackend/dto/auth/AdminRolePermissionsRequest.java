package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AdminRolePermissionsRequest(
        @NotNull Set<String> permissions,
        @NotNull Long version
) {
}
