package org.unipod.unipoderpbackend.dto.auth;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record UserSummaryResponse(
        UUID id,
        String email,
        ApiUserRole role,
        boolean active,
        Set<UUID> departmentIds,
        UserProfileResponse profile,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserSummaryResponse from(ApiUser user, Set<UUID> departmentIds, UserProfileResponse profile) {
        return new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                departmentIds,
                profile,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
