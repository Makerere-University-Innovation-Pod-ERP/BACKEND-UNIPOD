package org.unipod.unipoderpbackend.dto.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record AdminUserResponse(
        UUID id,
        String fullName,
        String email,
        ApiUserRole role,
        UUID departmentId,
        List<UUID> departmentIds,
        boolean active,
        long version,
        Instant createdAt
) {
    public static AdminUserResponse from(
            ApiUser user,
            String fullName,
            List<UUID> departmentIds
    ) {
        List<UUID> memberships = List.copyOf(departmentIds);
        UUID departmentId = memberships.isEmpty() ? null : memberships.get(0);
        return new AdminUserResponse(
                user.getId(),
                fullName,
                user.getEmail(),
                user.getRole(),
                departmentId,
                memberships,
                user.isEnabled(),
                user.getVersion(),
                user.getCreatedAt()
        );
    }
}
