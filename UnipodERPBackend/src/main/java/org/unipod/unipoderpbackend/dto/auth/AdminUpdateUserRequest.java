package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.NotNull;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import java.util.UUID;

public record AdminUpdateUserRequest(
        @NotNull ApiUserRole role,
        UUID departmentId,
        @NotNull Long version
) {
}
