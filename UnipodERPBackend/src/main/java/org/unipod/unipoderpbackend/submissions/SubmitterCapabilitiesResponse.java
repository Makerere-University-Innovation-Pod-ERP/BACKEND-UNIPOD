package org.unipod.unipoderpbackend.submissions;

import java.util.List;

public record SubmitterCapabilitiesResponse(
        boolean canRegisterSubmission,
        String registrationMessage,
        List<String> lockedModules
) {
    public static SubmitterCapabilitiesResponse forSubmitter() {
        return new SubmitterCapabilitiesResponse(
                false,
                "Only authorized intake roles can register a submission.",
                List.of("Reception", "Reviews", "Facilities", "Finance", "Sectors", "Dashboard")
        );
    }
}
