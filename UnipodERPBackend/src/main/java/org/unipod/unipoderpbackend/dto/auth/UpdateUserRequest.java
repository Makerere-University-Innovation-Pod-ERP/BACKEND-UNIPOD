package org.unipod.unipoderpbackend.dto.auth;

import java.util.Set;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record UpdateUserRequest(
        ApiUserRole role,
        Set<UUID> departmentIds
) {
}
