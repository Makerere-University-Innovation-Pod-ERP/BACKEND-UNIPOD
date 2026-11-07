package org.unipod.unipoderpbackend.submissions;

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
        return new SubmitterCapabilitiesResponse(
                canRegister,
                canRegister
                        ? "Your intake role can register a submission."
                        : "Only authorized intake roles can register a submission.",
                List.of("Reception", "Reviews", "Facilities", "Finance", "Sectors", "Dashboard")
        );
    }
}
