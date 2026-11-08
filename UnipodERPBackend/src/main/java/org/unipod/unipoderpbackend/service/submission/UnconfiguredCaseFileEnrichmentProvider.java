package org.unipod.unipoderpbackend.service.submission;

import java.util.List;
import java.util.UUID;

public class UnconfiguredCaseFileEnrichmentProvider implements CaseFileEnrichmentProvider {
    @Override
    public ReviewPanel reviewPanel(UUID caseId) {
        return new ReviewPanel(Availability.NOT_CONFIGURED, List.of(), List.of(), false, false);
    }

    @Override
    public ActivityFeed activity(UUID caseId) {
        return new ActivityFeed(Availability.NOT_CONFIGURED, List.of());
    }
}
