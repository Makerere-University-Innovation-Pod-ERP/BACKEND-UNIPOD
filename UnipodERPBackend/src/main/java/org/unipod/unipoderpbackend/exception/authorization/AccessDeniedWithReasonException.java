package org.unipod.unipoderpbackend.exception.authorization;

import org.springframework.security.access.AccessDeniedException;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationDecision;

public class AccessDeniedWithReasonException extends AccessDeniedException {
    private final transient AuthorizationDecision decision;

    public AccessDeniedWithReasonException(AuthorizationDecision decision) {
        super(decision.reason());
        this.decision = decision;
    }

    public AuthorizationDecision getDecision() {
        return decision;
    }
}
