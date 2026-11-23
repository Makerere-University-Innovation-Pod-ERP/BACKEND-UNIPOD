package org.unipod.unipoderpbackend.dto.auth;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record AdminUserResponse(
        UUID id,
        String fullName,
        String email,
        ApiUserRole role,
        UUID departmentId,
        boolean active,
        long version,
        Instant createdAt
) {
    public static AdminUserResponse from(
            ApiUser user,
            String fullName,
            UUID departmentId
    ) {
        return new AdminUserResponse(
                user.getId(),
                fullName,
                user.getEmail(),
                user.getRole(),
                departmentId,
                user.isEnabled(),
                user.getVersion(),
                user.getCreatedAt()
        );
    }
}
