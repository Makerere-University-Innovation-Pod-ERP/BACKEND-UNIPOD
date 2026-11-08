package org.unipod.unipoderpbackend.service.dashboard;

import java.util.List;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.ActivityFeed;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.FinanceSummary;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.OperationsSummary;
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
            public OperationsSummary operations() {
                return new OperationsSummary("NOT_CONFIGURED", "Fidel operations integration is not configured.",
                        null, null, null);
            }

            @Override
            public FinanceSummary finance() {
                return new FinanceSummary("NOT_CONFIGURED", "David finance integration is not configured.",
                        null, List.of());
            }

            @Override
            public ActivityFeed recentActivity() {
                return new ActivityFeed("NOT_CONFIGURED", "David audit integration is not configured.",
                        List.of());
            }
        };
    }
}
