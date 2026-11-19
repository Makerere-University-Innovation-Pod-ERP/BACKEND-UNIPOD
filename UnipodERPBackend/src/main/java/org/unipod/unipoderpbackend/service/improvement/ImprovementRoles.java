package org.unipod.unipoderpbackend.service.improvement;

import org.springframework.security.core.Authentication;

/**
 * Role helpers shared by the improvement read and accept services (moved unchanged from the removed
 * service.improvement.RefinementRequestService; asking for changes is now main's CaseFileController).
 */
final class ImprovementRoles {
    static final String STAFF_ROLES = "hasAnyRole('FACT_FINDING_OFFICER', 'INNOVATION_COORDINATOR', 'ADMINISTRATOR')";

    private ImprovementRoles() {
    }

    static boolean has(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
