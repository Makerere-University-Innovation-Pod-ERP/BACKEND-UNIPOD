package org.unipod.unipoderpbackend.service.authorization;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.unipod.unipoderpbackend.exception.authorization.AccessDeniedWithReasonException;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationDecision;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationResource;
import org.unipod.unipoderpbackend.model.authorization.DenialCode;
import org.unipod.unipoderpbackend.model.authorization.Scope;

/**
 * The one place that answers "is this person allowed to do this to this record?".
 * Every endpoint should call {@link #authorize} before doing any work.
 */
@Service
public class AuthorizationService {
    private final AuthorizationPolicy policy;

    public AuthorizationService(AuthorizationPolicy policy) {
        this.policy = policy;
    }

    /** Returns the decision without throwing. Use it to build capability lists (which buttons to show). */
    public AuthorizationDecision check(Actor actor, AuthorizationAction action, AuthorizationResource resource) {
        Optional<Scope> scope = policy.scopeFor(actor.role(), action);
        if (scope.isEmpty()) {
            return AuthorizationDecision.deny(DenialCode.ROLE_NOT_PERMITTED);
        }
        return switch (scope.get()) {
            case ALL -> AuthorizationDecision.allow();
            case OWN -> actor.email().equals(resource.ownerEmail())
                    ? AuthorizationDecision.allow()
                    : AuthorizationDecision.deny(DenialCode.NOT_OWNER);
            case ASSIGNED -> resource.assigneeEmails().contains(actor.email())
                    ? AuthorizationDecision.allow()
                    : AuthorizationDecision.deny(DenialCode.NOT_ASSIGNED);
            case DEPARTMENT -> resource.departmentId() != null
                    && actor.departmentIds().contains(resource.departmentId())
                    ? AuthorizationDecision.allow()
                    : AuthorizationDecision.deny(DenialCode.OUTSIDE_DEPARTMENT);
        };
    }

    /** Throws {@link AccessDeniedWithReasonException} (HTTP 403 with code and reason) when not allowed. */
    public void authorize(Actor actor, AuthorizationAction action, AuthorizationResource resource) {
        AuthorizationDecision decision = check(actor, action, resource);
        if (!decision.allowed()) {
            throw new AccessDeniedWithReasonException(decision);
        }
    }
}
