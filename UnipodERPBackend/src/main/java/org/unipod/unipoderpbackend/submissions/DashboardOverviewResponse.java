package org.unipod.unipoderpbackend.submissions;

import java.util.Map;

public record DashboardOverviewResponse(
        long totalSubmissions,
        Map<SubmissionStage, Long> byStage,
        long studentSubmissions,
        long generalSubmissions,
        long awaitingActionCount,
        long forwardedCount,
        long completedCount,
        long recentlyUpdatedCount
) {
}
