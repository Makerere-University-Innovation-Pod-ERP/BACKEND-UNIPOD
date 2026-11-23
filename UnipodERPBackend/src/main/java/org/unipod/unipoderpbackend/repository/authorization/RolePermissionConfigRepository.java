package org.unipod.unipoderpbackend.repository.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.RolePermissionConfig;

public interface RolePermissionConfigRepository extends JpaRepository<RolePermissionConfig, ApiUserRole> {
}
