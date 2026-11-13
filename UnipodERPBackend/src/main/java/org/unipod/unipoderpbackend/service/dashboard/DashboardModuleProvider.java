package org.unipod.unipoderpbackend.service.dashboard;

import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.ActivityFeed;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.EquipmentReturns;

public interface DashboardModuleProvider {
    EquipmentReturns overdueEquipmentReturns();

    ActivityFeed recentActivity();
}
