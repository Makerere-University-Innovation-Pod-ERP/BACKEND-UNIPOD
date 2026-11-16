package org.unipod.unipoderpbackend.dto.auth;

import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record RoleResponse(ApiUserRole role, boolean staff) {
    public static RoleResponse from(ApiUserRole role) {
        return new RoleResponse(role, role != ApiUserRole.SUBMITTER);
    }
}
