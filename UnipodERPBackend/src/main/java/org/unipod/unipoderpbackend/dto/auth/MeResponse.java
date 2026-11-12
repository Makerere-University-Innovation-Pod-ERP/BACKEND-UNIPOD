package org.unipod.unipoderpbackend.dto.auth;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record MeResponse(
        UUID id,
        String email,
        ApiUserRole role,
        boolean mustChangePassword,
        String homeScreen,
        Set<UUID> departmentIds,
        UserProfileResponse profile,
        List<CapabilityResponse> capabilities
) {
}
