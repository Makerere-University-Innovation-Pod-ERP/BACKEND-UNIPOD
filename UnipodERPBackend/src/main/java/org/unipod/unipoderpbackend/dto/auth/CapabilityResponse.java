package org.unipod.unipoderpbackend.dto.auth;

import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.Scope;

public record CapabilityResponse(AuthorizationAction action, Scope scope) {
}
