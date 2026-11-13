package org.unipod.unipoderpbackend.service.dashboard;

import java.util.List;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.ActivityFeed;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.EquipmentReturns;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UnconfiguredDashboardModuleProvider {
    @Bean
    @ConditionalOnMissingBean(DashboardModuleProvider.class)
    DashboardModuleProvider dashboardModuleProvider() {
        return new DashboardModuleProvider() {
            @Override
            public EquipmentReturns overdueEquipmentReturns() {
                return new EquipmentReturns("NOT_CONFIGURED",
                        "Fidel equipment returns integration is not configured.", null);
            }

            @Override
            public ActivityFeed recentActivity() {
                return new ActivityFeed("NOT_CONFIGURED", "David audit integration is not configured.",
                        List.of());
            }
        };
    }
}
