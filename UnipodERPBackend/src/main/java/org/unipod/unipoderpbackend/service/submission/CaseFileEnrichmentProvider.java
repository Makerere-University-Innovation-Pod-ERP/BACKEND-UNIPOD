package org.unipod.unipoderpbackend.service.submission;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CaseFileEnrichmentProvider {
    ReviewPanel reviewPanel(UUID caseId);

    ActivityFeed activity(UUID caseId);

    enum Availability {
        AVAILABLE,
        NOT_CONFIGURED
    }

    record ReviewPanel(
            Availability availability,
            List<String> reviewerTeam,
            List<String> ipPanel,
            boolean quorumAvailable,
            boolean quorumMet
    ) {
    }

    record ActivityFeed(Availability availability, List<ActivityItem> items) {
    }

    record ActivityItem(Instant occurredAt, String actor, String action, String summary) {
    }
}
