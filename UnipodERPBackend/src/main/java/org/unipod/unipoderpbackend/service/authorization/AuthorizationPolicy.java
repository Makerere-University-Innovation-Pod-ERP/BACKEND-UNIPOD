package org.unipod.unipoderpbackend.service.authorization;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.Scope;

import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.AUDIT_VIEW;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_ASSIGN_REVIEWER;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_DECIDE;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_EDIT;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_FORWARD;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_REVIEW;
import static org.unipod.unipoderpbackend.model.authorization.AuthorizationAction.CASE_VIEW;

@Component
public class AuthorizationPolicy {
    private final Map<ApiUserRole, Map<AuthorizationAction, Scope>> rules = new EnumMap<>(ApiUserRole.class);

    public AuthorizationPolicy() {
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
                AUDIT_VIEW, Scope.ALL
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
                AUDIT_VIEW, Scope.ALL
        ));
        rules.put(ApiUserRole.IP_LIAISON, Map.of(
                CASE_VIEW, Scope.ALL
        ));
    }

    public Map<AuthorizationAction, Scope> capabilitiesFor(ApiUserRole role) {
        Map<AuthorizationAction, Scope> capabilities = new EnumMap<>(AuthorizationAction.class);
        capabilities.putAll(rules.getOrDefault(role, Map.of()));
        return capabilities;
    }

    public Optional<Scope> scopeFor(ApiUserRole role, AuthorizationAction action) {
        return Optional.ofNullable(rules.getOrDefault(role, Map.of()).get(action));
    }
}
