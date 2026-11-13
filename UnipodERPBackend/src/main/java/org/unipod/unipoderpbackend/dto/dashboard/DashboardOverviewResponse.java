package org.unipod.unipoderpbackend.dto.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

public record DashboardOverviewResponse(
        long totalBriefs,
        long waitingForReview,
        long needingImprovement,
        long completedThisMonth,
        Map<DashboardPipelineStage, Long> pipeline,
        List<NeedsAttentionCase> needsAttention,
        EquipmentReturns overdueEquipmentReturns,
        ActivityFeed recentActivity
) {
    public record NeedsAttentionCase(
            UUID id,
            String code,
            String title,
            SubmissionStage stage,
            String reason,
            Instant updatedAt
    ) {
    }

    public record EquipmentReturns(
            String availability,
            String reason,
            Long overdueCount
    ) {
    }

    public record ActivityFeed(
            String availability,
            String reason,
            List<ActivityItem> items
    ) {
    }

    public record ActivityItem(Instant occurredAt, String actor, String action, String summary) {
    }
}
