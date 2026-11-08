package org.unipod.unipoderpbackend.service.dashboard;

import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.ActivityFeed;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.FinanceSummary;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.OperationsSummary;

public interface DashboardModuleProvider {
    OperationsSummary operations();

    FinanceSummary finance();

    ActivityFeed recentActivity();
}
