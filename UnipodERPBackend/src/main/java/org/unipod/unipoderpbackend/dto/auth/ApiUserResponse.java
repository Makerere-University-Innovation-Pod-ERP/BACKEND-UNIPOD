package org.unipod.unipoderpbackend.dto.auth;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record ApiUserResponse(
        UUID id,
        String email,
        ApiUserRole role,
        Instant createdAt
) {
    public static ApiUserResponse from(ApiUser user) {
        return new ApiUserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
