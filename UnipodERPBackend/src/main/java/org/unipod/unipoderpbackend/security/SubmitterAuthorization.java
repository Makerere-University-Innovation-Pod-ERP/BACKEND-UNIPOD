package org.unipod.unipoderpbackend.security;

import org.springframework.security.core.Authentication;

public final class SubmitterAuthorization {
    private SubmitterAuthorization() {
    }

    public static boolean hasSubmitterRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUBMITTER"));
    }
}
