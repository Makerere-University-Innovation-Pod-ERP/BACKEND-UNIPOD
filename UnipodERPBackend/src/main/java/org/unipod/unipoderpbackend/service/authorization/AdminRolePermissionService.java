package org.unipod.unipoderpbackend.service.authorization;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.auth.AdminPermissionResponse;
import org.unipod.unipoderpbackend.dto.auth.AdminRoleResponse;
import org.unipod.unipoderpbackend.dto.auth.AdminRolePermissionsRequest;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.RolePermissionConfig;
import org.unipod.unipoderpbackend.repository.authorization.RolePermissionConfigRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;

@Service
public class AdminRolePermissionService {
    private static final List<AdminPermissionResponse> PERMISSIONS = Arrays.stream(AuthorizationAction.values())
            .map(AdminRolePermissionService::permission)
            .toList();

    private final RolePermissionConfigRepository configs;
    private final AuditService audit;

    public AdminRolePermissionService(RolePermissionConfigRepository configs, AuditService audit) {
        this.configs = configs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdminRoleResponse> roles() {
        return Arrays.stream(ApiUserRole.values())
                .map(role -> roleResponse(role, configs.findById(role).orElse(null)))
                .toList();
    }

    public List<AdminPermissionResponse> permissions() {
        return PERMISSIONS;
    }

    @Transactional
    public AdminRoleResponse update(ApiUserRole role, AdminRolePermissionsRequest request, Actor actor) {
        if (role == ApiUserRole.ADMINISTRATOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The administrator role is protected.");
        }
        Set<String> validPermissions = Arrays.stream(AuthorizationAction.values())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
        if (!validPermissions.containsAll(request.permissions())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more permissions are invalid.");
        }
        RolePermissionConfig config = configs.findById(role)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Permission settings are not initialized for this role."
                ));
        if (request.version() != config.getVersion()) {
            throw conflict();
        }
        Set<String> before = config.getPermissions();
        if (before.equals(request.permissions())) {
            return roleResponse(role, config);
        }
        config.replacePermissions(request.permissions(), Instant.now());
        try {
            configs.saveAndFlush(config);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw conflict();
        }
        audit.log(AuditEvent.changed(
                actor,
                "role.permissions_changed",
                "role_permissions",
                UUID.nameUUIDFromBytes(role.name().getBytes(StandardCharsets.UTF_8)),
                Map.of("permissions", before.stream().sorted().toList()),
                Map.of("permissions", request.permissions().stream().sorted().toList())
        ));
        return roleResponse(role, config);
    }

    private static AdminRoleResponse roleResponse(ApiUserRole role, RolePermissionConfig config) {
        List<String> permissions = role == ApiUserRole.ADMINISTRATOR
                ? Arrays.stream(AuthorizationAction.values()).map(Enum::name).sorted().toList()
                : config == null ? List.of() : config.getPermissions().stream().sorted().toList();
        return new AdminRoleResponse(
                role,
                displayName(role),
                permissions,
                config == null ? 0 : config.getVersion(),
                role != ApiUserRole.ADMINISTRATOR
        );
    }

    private static AdminPermissionResponse permission(AuthorizationAction action) {
        return switch (action) {
            case CASE_VIEW -> new AdminPermissionResponse(action.name(), "View submissions", "View submissions within the role's assigned scope.");
            case CASE_EDIT -> new AdminPermissionResponse(action.name(), "Edit submissions", "Edit submissions within the role's assigned scope.");
            case CASE_FORWARD -> new AdminPermissionResponse(action.name(), "Route submissions", "Forward submissions to the next workflow stage.");
            case CASE_ASSIGN_REVIEWER -> new AdminPermissionResponse(action.name(), "Assign reviewers", "Assign reviewers to submissions.");
            case CASE_REVIEW -> new AdminPermissionResponse(action.name(), "Review submissions", "Submit reviews for assigned submissions.");
            case CASE_DECIDE -> new AdminPermissionResponse(action.name(), "Decide submissions", "Make workflow decisions on submissions.");
            case AUDIT_VIEW -> new AdminPermissionResponse(action.name(), "View audit log", "View recorded system activity.");
            case USER_MANAGE -> new AdminPermissionResponse(action.name(), "Manage user accounts", "Create and manage user accounts.");
            case PRICE_LIST_MANAGE -> new AdminPermissionResponse(action.name(), "Manage finance", "Manage price lists and finance operations.");
            case PRICE_LIST_PROPOSE -> new AdminPermissionResponse(action.name(), "Propose price list updates", "Propose price-list updates within the assigned scope.");
            case REPORT_VIEW -> new AdminPermissionResponse(action.name(), "View reports", "View system reports.");
        };
    }

    private static String displayName(ApiUserRole role) {
        String[] parts = role.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This role's permissions changed elsewhere. Refresh and try again."
        );
    }
}
