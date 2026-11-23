package org.unipod.unipoderpbackend.controller.auth;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.auth.AdminPermissionResponse;
import org.unipod.unipoderpbackend.dto.auth.AdminRolePermissionsRequest;
import org.unipod.unipoderpbackend.dto.auth.AdminRoleResponse;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.authorization.AdminRolePermissionService;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminRoleController {
    private final AdminRolePermissionService roles;
    private final CurrentActor currentActor;

    public AdminRoleController(AdminRolePermissionService roles, CurrentActor currentActor) {
        this.roles = roles;
        this.currentActor = currentActor;
    }

    @GetMapping("/roles")
    public List<AdminRoleResponse> roles() {
        return roles.roles();
    }

    @GetMapping("/permissions")
    public List<AdminPermissionResponse> permissions() {
        return roles.permissions();
    }

    @PutMapping("/roles/{id}/permissions")
    public AdminRoleResponse updatePermissions(
            @PathVariable ApiUserRole id,
            @Valid @RequestBody AdminRolePermissionsRequest request,
            Authentication authentication
    ) {
        return roles.update(id, request, currentActor.from(authentication));
    }
}
