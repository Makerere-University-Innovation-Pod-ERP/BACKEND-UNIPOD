package org.unipod.unipoderpbackend.dto.auth;

import java.util.List;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record AdminRoleResponse(
        ApiUserRole id,
        String name,
        List<String> permissions,
        long version,
        boolean editable
) {
}
