package org.unipod.unipoderpbackend.dto.submission;

import java.util.List;
import org.springframework.security.core.Authentication;

public record SubmitterCapabilitiesResponse(
        boolean canRegisterSubmission,
        String registrationMessage,
        List<String> lockedModules
) {
    public static SubmitterCapabilitiesResponse forSubmitter(Authentication authentication) {
        boolean canRegister = authentication.getAuthorities().stream()
                .anyMatch(authority -> switch (authority.getAuthority()) {
                    case "ROLE_ADMINISTRATOR",
                         "ROLE_INNOVATION_COORDINATOR",
                         "ROLE_RECEPTIONIST",
                         "ROLE_FACT_FINDING_OFFICER" -> true;
                    default -> false;
                });
        boolean isSubmitter = hasSubmitterRole(authentication);
        return new SubmitterCapabilitiesResponse(
                canRegister,
                isSubmitter && !canRegister
                        ? "You can submit a brief for reception review. Only authorized intake roles can register a submission."
                        : canRegister
                                ? "Your intake role can register a submission."
                                : "Only authorized intake roles can register a submission.",
                List.of("Reception", "Reviews", "Facilities", "Finance", "Sectors", "Dashboard")
        );
    }

    private static boolean hasSubmitterRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUBMITTER"));
    }
}
