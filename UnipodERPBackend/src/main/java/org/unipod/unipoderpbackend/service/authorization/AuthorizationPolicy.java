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

/**
 * Which role may do which action, and on how many records.
 *
 * <p>Anything not listed is denied. This is a first draft built from the
 * assignment doc, section 2; confirm it with Waluube and Mark. It lives in one
 * place so it can later move to ConfigService without touching callers.</p>
 */
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
                CASE_FORWARD, Scope.ALL
        ));
        rules.put(ApiUserRole.FACT_FINDING_OFFICER, Map.of(
                CASE_VIEW, Scope.ALL
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
        // IP_LIAISON has no rules: IP is out of scope, so every action is denied.
    }

    /**
     * The scope a role has for an action, or empty if the role may not do it at all.
     * List endpoints use this to filter their queries (e.g. OWN means "WHERE submitter_email = me").
     */
    public Optional<Scope> scopeFor(ApiUserRole role, AuthorizationAction action) {
        return Optional.ofNullable(rules.getOrDefault(role, Map.of()).get(action));
    }
}
