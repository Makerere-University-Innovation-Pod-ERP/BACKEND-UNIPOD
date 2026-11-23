package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Set;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record UpdateUserRequest(
        ApiUserRole role,
        Set<UUID> departmentIds,
        @NotNull @PositiveOrZero Long version
) {
    public UpdateUserRequest(ApiUserRole role, Set<UUID> departmentIds) {
        this(role, departmentIds, null);
    }
}
