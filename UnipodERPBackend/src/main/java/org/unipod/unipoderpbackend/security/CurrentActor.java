package org.unipod.unipoderpbackend.security;

import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;

/**
 * Turns the Spring Security login (from the JWT) into an {@link Actor}.
 * Controllers call {@code CurrentActor.from(authentication)} and pass the result to the service.
 */
public final class CurrentActor {
    private static final String ROLE_PREFIX = "ROLE_";

    private CurrentActor() {
    }

    public static Actor from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Not signed in.");
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
        if (roles.size() != 1) {
            throw new AccessDeniedException("Expected exactly one role.");
        }
        ApiUserRole role;
        try {
            role = ApiUserRole.valueOf(roles.get(0));
        } catch (IllegalArgumentException unknownRole) {
            throw new AccessDeniedException("Unknown role.");
        }
        // Departments are empty until the team decides where they live, so DEPARTMENT scope denies (fails closed).
        return new Actor(authentication.getName(), role, Set.of());
    }
}
