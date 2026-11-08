package org.unipod.unipoderpbackend.service.dashboard;

import org.springframework.security.core.Authentication;

final class DashboardVisibility {
    private DashboardVisibility() {
    }

    static boolean canViewFinance(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(role -> role.equals("ROLE_ADMINISTRATOR")
                        || role.equals("ROLE_INNOVATION_COORDINATOR"));
    }
}
