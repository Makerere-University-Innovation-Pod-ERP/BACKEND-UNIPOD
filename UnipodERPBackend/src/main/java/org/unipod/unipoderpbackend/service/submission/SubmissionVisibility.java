package org.unipod.unipoderpbackend.service.submission;

import org.springframework.security.core.Authentication;

public final class SubmissionVisibility {
    private SubmissionVisibility() {
    }

    public static String scope(Authentication authentication) {
        if (has(authentication, "ADMINISTRATOR", "INNOVATION_COORDINATOR", "RECEPTIONIST",
                "FACT_FINDING_OFFICER", "OBSERVER")) {
            return "ALL";
        }
        boolean reviewer = has(authentication, "REVIEWER");
        boolean sectorOfficer = has(authentication, "SECTOR_OFFICER");
        if (reviewer && sectorOfficer) {
            return "REVIEWER_OR_SECTOR_OFFICER";
        }
        if (reviewer) {
            return "REVIEWER";
        }
        if (sectorOfficer) {
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
