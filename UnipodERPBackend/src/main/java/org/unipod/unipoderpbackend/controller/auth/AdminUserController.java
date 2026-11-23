package org.unipod.unipoderpbackend.controller.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.unipod.unipoderpbackend.dto.auth.AdminCreateUserRequest;
import org.unipod.unipoderpbackend.dto.auth.AdminUpdateUserRequest;
import org.unipod.unipoderpbackend.dto.auth.AdminUserResponse;
import org.unipod.unipoderpbackend.dto.auth.AdminUserStatusRequest;
import org.unipod.unipoderpbackend.dto.auth.UpdateUserRequest;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.auth.UserAdministrationService;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final UserAdministrationService users;
    private final CurrentActor currentActor;

    public AdminUserController(UserAdministrationService users, CurrentActor currentActor) {
        this.users = users;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public List<AdminUserResponse> list(Authentication authentication) {
        return users.adminList(currentActor.from(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public AdminUserResponse create(
            @Valid @RequestBody AdminCreateUserRequest request,
            Authentication authentication
    ) {
        return users.createManagedUser(request, currentActor.from(authentication));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public AdminUserResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUpdateUserRequest request,
            Authentication authentication
    ) {
        users.update(
                currentActor.from(authentication),
                id,
                new UpdateUserRequest(
                        request.role(),
                        request.departmentId() == null ? Set.of() : Set.of(request.departmentId()),
                        request.version()
                )
        );
        return users.getAdminUser(currentActor.from(authentication), id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public AdminUserResponse setActive(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserStatusRequest request,
            Authentication authentication
    ) {
        var actor = currentActor.from(authentication);
        if (request.active()) {
            users.reactivate(actor, id, request.version());
        } else {
            users.deactivate(actor, id, request.version());
        }
        return users.getAdminUser(actor, id);
    }
}
