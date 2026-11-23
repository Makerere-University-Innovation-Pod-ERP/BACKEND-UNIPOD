package org.unipod.unipoderpbackend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public record CreateApiUserRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotNull ApiUserRole role,
        @Size(max = 200) String fullName,
        UUID departmentId
) {
    public CreateApiUserRequest(String email, String password, ApiUserRole role) {
        this(email, password, role, null, null);
    }
}
