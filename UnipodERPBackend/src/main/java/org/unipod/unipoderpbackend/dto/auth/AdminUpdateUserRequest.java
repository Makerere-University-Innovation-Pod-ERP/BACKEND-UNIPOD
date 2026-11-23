package org.unipod.unipoderpbackend.dto.auth;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

public class AdminUpdateUserRequest {
    @NotNull
    private ApiUserRole role;

    private UUID departmentId;
    private boolean departmentIdProvided;

    @NotNull
    @PositiveOrZero
    private Long version;

    public ApiUserRole role() {
        return role;
    }

    public UUID departmentId() {
        return departmentId;
    }

    public boolean departmentIdProvided() {
        return departmentIdProvided;
    }

    public Long version() {
        return version;
    }

    public void setRole(ApiUserRole role) {
        this.role = role;
    }

    @JsonSetter("departmentId")
    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
        this.departmentIdProvided = true;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
