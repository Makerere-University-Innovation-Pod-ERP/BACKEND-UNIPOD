package org.unipod.unipoderpbackend.controller.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.auth.RoleResponse;
import org.unipod.unipoderpbackend.dto.auth.UpdateUserRequest;
import org.unipod.unipoderpbackend.dto.auth.UserSummaryResponse;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.auth.UserAdministrationService;

@RestController
@Validated
public class UserController {
    private final UserAdministrationService users;
    private final CurrentActor currentActor;

    public UserController(UserAdministrationService users, CurrentActor currentActor) {
        this.users = users;
        this.currentActor = currentActor;
    }

    @GetMapping("/users")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<UserSummaryResponse> list(
            @RequestParam(required = false) ApiUserRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @Size(max = 254) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return users.list(currentActor.from(authentication), role, active, q, page, size);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("isAuthenticated()")
    public UserSummaryResponse get(@PathVariable UUID id, Authentication authentication) {
        return users.get(currentActor.from(authentication), id);
    }

    @PatchMapping("/users/{id}")
    @PreAuthorize("isAuthenticated()")
    public UserSummaryResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication
    ) {
        return users.update(currentActor.from(authentication), id, request);
    }

    @PostMapping("/users/{id}/deactivate")
    @PreAuthorize("isAuthenticated()")
    public UserSummaryResponse deactivate(@PathVariable UUID id, Authentication authentication) {
        return users.deactivate(currentActor.from(authentication), id);
    }

    @PostMapping("/users/{id}/reactivate")
    @PreAuthorize("isAuthenticated()")
    public UserSummaryResponse reactivate(@PathVariable UUID id, Authentication authentication) {
        return users.reactivate(currentActor.from(authentication), id);
    }

    @GetMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    public List<RoleResponse> roles() {
        return users.roles();
    }
}
