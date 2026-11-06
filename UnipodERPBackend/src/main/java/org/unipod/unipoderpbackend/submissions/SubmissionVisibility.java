package org.unipod.unipoderpbackend.submissions;

import org.springframework.security.core.Authentication;

final class SubmissionVisibility {
    private SubmissionVisibility() {
    }

    static String scope(Authentication authentication) {
        if (has(authentication, "ADMINISTRATOR", "INNOVATION_COORDINATOR", "RECEPTIONIST",
                "FACT_FINDING_OFFICER", "IP_LIAISON", "OBSERVER")) {
            return "ALL";
        }
        if (has(authentication, "REVIEWER")) {
            return "REVIEWER";
        }
        if (has(authentication, "SECTOR_OFFICER")) {
            return "SECTOR_OFFICER";
        }
        return "SUBMITTER";
    }

    private static boolean has(Authentication authentication, String... roles) {
        for (String role : roles) {
            if (authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role))) {
                return true;
            }
        }
        return false;
    }
}
