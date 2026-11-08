package org.unipod.unipoderpbackend.controller.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.auth.ApiUserResponse;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AuthenticationService authentication;

    public AdminUserController(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ApiUserResponse create(@Valid @RequestBody CreateApiUserRequest request) {
        return authentication.createUser(request);
    }
}
