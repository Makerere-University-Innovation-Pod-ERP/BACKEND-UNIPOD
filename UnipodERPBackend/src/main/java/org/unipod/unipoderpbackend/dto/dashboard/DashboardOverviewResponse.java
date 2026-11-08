package org.unipod.unipoderpbackend.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

public record DashboardOverviewResponse(
        long totalSubmissions,
        Map<SubmissionStage, Long> byStage,
        long studentSubmissions,
        long generalSubmissions,
        long awaitingActionCount,
        long forwardedCount,
        long completedCount,
        long recentlyUpdatedCount,
        long awaitingReviewCount,
        long refinementRequestedCount,
        Map<DashboardPipelineStage, Long> pipelineAtAGlance,
        List<NeedsAttentionCase> needsAttention,
        OperationsSummary operations,
        FinanceSummary finance,
        ActivityFeed recentActivity
) {
    public DashboardOverviewResponse(
            long totalSubmissions,
            Map<SubmissionStage, Long> byStage,
            long studentSubmissions,
            long generalSubmissions,
            long awaitingActionCount,
            long forwardedCount,
            long completedCount,
            long recentlyUpdatedCount
    ) {
        this(totalSubmissions, byStage, studentSubmissions, generalSubmissions,
                awaitingActionCount, forwardedCount, completedCount, recentlyUpdatedCount,
                0, 0, Map.of(), List.of(), null, null, null);
    }

    public record NeedsAttentionCase(
            UUID id,
            String code,
            String title,
            SubmissionStage stage,
            String reason,
            Instant updatedAt
    ) {
    }

    public record OperationsSummary(
            String availability,
            String reason,
            Long equipmentUnderRepair,
            Long bookingsToday,
            Long facilitiesFreeNow
    ) {
    }

    public record FinanceSummary(
            String availability,
            String reason,
            BigDecimal incomeMonthToDate,
            List<IncomeBar> incomeBars
    ) {
    }

    public record IncomeBar(String period, BigDecimal income) {
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
