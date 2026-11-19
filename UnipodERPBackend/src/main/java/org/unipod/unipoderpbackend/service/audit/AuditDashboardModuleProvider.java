package org.unipod.unipoderpbackend.service.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.ActivityFeed;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.ActivityItem;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.EquipmentReturns;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.service.dashboard.DashboardModuleProvider;

/**
 * Fills the managers' Overview recent-activity feed from the audit log. Overdue equipment
 * returns stay NOT_CONFIGURED until Fidel's equipment module provides them.
 */
@Component
public class AuditDashboardModuleProvider implements DashboardModuleProvider {
    static final int ITEMS = 10;
    private static final Instant END_OF_TIME = Instant.parse("9999-12-31T00:00:00Z");

    private final AuditEntryRepository entries;

    public AuditDashboardModuleProvider(AuditEntryRepository entries) {
        this.entries = entries;
    }

    @Override
    public EquipmentReturns overdueEquipmentReturns() {
        return new EquipmentReturns("NOT_CONFIGURED", "Fidel equipment returns integration is not configured.", null);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityFeed recentActivity() {
        List<ActivityItem> items = entries.search(null, null, null, null, Instant.EPOCH, END_OF_TIME, PageRequest.of(0, ITEMS))
                .stream()
                .map(entry -> new ActivityItem(entry.getOccurredAt(), entry.getActor(), entry.getAction(),
                        summary(entry.getAction(), entry.getEntityType())))
                .toList();
        return new ActivityFeed("AVAILABLE", null, items);
    }

    static String summary(String action, String entityType) {
        String words = action.replace('.', ' ').replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1) + " (" + entityType.replace('_', ' ') + ")";
    }
}
