package org.unipod.unipoderpbackend.model.authorization;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;

@Entity
@Table(name = "role_permission_configs")
public class RolePermissionConfig {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApiUserRole role;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_permission_grants",
            joinColumns = @JoinColumn(name = "role", referencedColumnName = "role")
    )
    @Column(name = "permission_id", nullable = false, length = 60)
    private Set<String> permissions = new HashSet<>();

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RolePermissionConfig() {
    }

    public RolePermissionConfig(ApiUserRole role, Set<String> permissions, Instant updatedAt) {
        this.role = role;
        this.permissions = new HashSet<>(permissions);
        this.updatedAt = updatedAt;
    }

    public ApiUserRole getRole() {
        return role;
    }

    public Set<String> getPermissions() {
        return Set.copyOf(permissions);
    }

    public long getVersion() {
        return version;
    }

    public void replacePermissions(Set<String> permissions, Instant updatedAt) {
        this.permissions.clear();
        this.permissions.addAll(permissions);
        this.updatedAt = updatedAt;
    }
}
