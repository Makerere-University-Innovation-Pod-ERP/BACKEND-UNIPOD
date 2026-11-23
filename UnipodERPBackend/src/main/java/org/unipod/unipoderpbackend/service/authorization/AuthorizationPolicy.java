package org.unipod.unipoderpbackend.service.authorization;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.RolePermissionConfig;
import org.unipod.unipoderpbackend.model.authorization.Scope;
import org.unipod.unipoderpbackend.repository.authorization.RolePermissionConfigRepository;

import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.AUDIT_VIEW;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_ASSIGN_REVIEWER;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_DECIDE;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_EDIT;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_FORWARD;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_REVIEW;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_VIEW;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.PRICE_LIST_MANAGE;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.PRICE_LIST_PROPOSE;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.REPORT_VIEW;

@Component
public class AuthorizationPolicy {
    private final Map<ApiUserRole, Map<AuthorizationAction, Scope>> rules = new EnumMap<>(ApiUserRole.class);
    private final RolePermissionConfigRepository rolePermissions;

    public AuthorizationPolicy() {
        this(null);
    }

    @Autowired
    public AuthorizationPolicy(RolePermissionConfigRepository rolePermissions) {
        this.rolePermissions = rolePermissions;
        Map<AuthorizationAction, Scope> administrator = new EnumMap<>(AuthorizationAction.class);
        for (AuthorizationAction action : AuthorizationAction.values()) {
            administrator.put(action, Scope.ALL);
        }
        rules.put(ApiUserRole.ADMINISTRATOR, administrator);

        rules.put(ApiUserRole.INNOVATION_COORDINATOR, Map.of(
                CASE_VIEW, Scope.ALL,
                CASE_EDIT, Scope.ALL,
                CASE_ASSIGN_REVIEWER, Scope.ALL,
                CASE_DECIDE, Scope.ALL,
                AUDIT_VIEW, Scope.ALL,
                REPORT_VIEW, Scope.ALL
        ));
        rules.put(ApiUserRole.RECEPTIONIST, Map.of(
                CASE_VIEW, Scope.ALL,
                CASE_EDIT, Scope.ALL,
                CASE_FORWARD, Scope.ALL
        ));
        rules.put(ApiUserRole.FACT_FINDING_OFFICER, Map.of(
                CASE_VIEW, Scope.ALL,
                CASE_EDIT, Scope.ALL
        ));
        rules.put(ApiUserRole.REVIEWER, Map.of(
                CASE_VIEW, Scope.ASSIGNED,
                CASE_REVIEW, Scope.ASSIGNED
        ));
        rules.put(ApiUserRole.SECTOR_OFFICER, Map.of(
                CASE_VIEW, Scope.DEPARTMENT
        ));
        rules.put(ApiUserRole.SUBMITTER, Map.of(
                CASE_VIEW, Scope.OWN,
                CASE_EDIT, Scope.OWN
        ));
        rules.put(ApiUserRole.OBSERVER, Map.of(
                CASE_VIEW, Scope.ALL,
                AUDIT_VIEW, Scope.ALL,
                REPORT_VIEW, Scope.ALL
        ));
        rules.put(ApiUserRole.DEPARTMENT_COORDINATOR, Map.of(
                CASE_VIEW, Scope.DEPARTMENT,
                PRICE_LIST_PROPOSE, Scope.DEPARTMENT
        ));
        rules.put(ApiUserRole.FINANCE_OFFICER, Map.of(
                PRICE_LIST_MANAGE, Scope.ALL
        ));
    }

    public Map<AuthorizationAction, Scope> capabilitiesFor(ApiUserRole role) {
        if (role == ApiUserRole.ADMINISTRATOR) {
            return Map.copyOf(rules.get(role));
        }
        Map<AuthorizationAction, Scope> capabilities = new EnumMap<>(AuthorizationAction.class);
        Optional<RolePermissionConfig> config = configuredPermissions(role);
        if (config.isPresent()) {
            for (AuthorizationAction action : AuthorizationAction.values()) {
                if (config.get().getPermissions().contains(action.name())) {
                    capabilities.put(action, configuredScope(role, action));
                }
            }
        } else {
            capabilities.putAll(rules.getOrDefault(role, Map.of()));
        }
        return capabilities;
    }

    public Optional<Scope> scopeFor(ApiUserRole role, AuthorizationAction action) {
        if (role == ApiUserRole.ADMINISTRATOR) {
            return Optional.of(Scope.ALL);
        }
        Optional<RolePermissionConfig> config = configuredPermissions(role);
        if (config.isPresent()) {
            return config.get().getPermissions().contains(action.name())
                    ? Optional.of(configuredScope(role, action))
                    : Optional.empty();
        }
        return Optional.ofNullable(rules.getOrDefault(role, Map.of()).get(action));
    }

    private Optional<RolePermissionConfig> configuredPermissions(ApiUserRole role) {
        return rolePermissions == null ? Optional.empty() : rolePermissions.findById(role);
    }

    private Scope configuredScope(ApiUserRole role, AuthorizationAction action) {
        Scope original = rules.getOrDefault(role, Map.of()).get(action);
        if (original != null) {
            return original;
        }
        return switch (action) {
            case CASE_VIEW, CASE_EDIT, CASE_FORWARD, CASE_ASSIGN_REVIEWER, CASE_REVIEW, CASE_DECIDE ->
                    caseScope(role);
            case PRICE_LIST_PROPOSE -> role == ApiUserRole.DEPARTMENT_COORDINATOR
                    || role == ApiUserRole.SECTOR_OFFICER ? Scope.DEPARTMENT : Scope.OWN;
            default -> Scope.OWN;
        };
    }

    private static Scope caseScope(ApiUserRole role) {
        return switch (role) {
            case SUBMITTER -> Scope.OWN;
            case REVIEWER -> Scope.ASSIGNED;
            case DEPARTMENT_COORDINATOR, SECTOR_OFFICER -> Scope.DEPARTMENT;
            default -> Scope.OWN;
        };
    }
}
