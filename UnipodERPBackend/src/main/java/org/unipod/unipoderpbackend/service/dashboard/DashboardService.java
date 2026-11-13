package org.unipod.unipoderpbackend.service.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.repository.dashboard.PipelineMetricsRepository;

@Service
public class DashboardService {
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration STALE_AFTER = Duration.ofDays(14);

    private final PipelineMetricsRepository metrics;
    private final DashboardModuleProvider modules;
    private final Clock clock;
    private final Map<String, CachedOverview> cache = new ConcurrentHashMap<>();

    public DashboardService(
            PipelineMetricsRepository metrics,
            DashboardModuleProvider modules,
            Clock clock
    ) {
        this.metrics = metrics;
        this.modules = modules;
        this.clock = clock;
    }

    public DashboardOverviewResponse overview(Authentication authentication) {
        String principal = authentication.getName();
        Instant now = clock.instant();
        cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        CachedOverview cached = cache.compute(principal, (key, existing) -> {
            if (existing != null && existing.expiresAt().isAfter(now)) {
                return existing;
            }
            return new CachedOverview(load(now), now.plus(CACHE_TTL));
        });
        return cached.response();
    }

    private DashboardOverviewResponse load(Instant now) {
        DashboardOverviewResponse workflow = metrics.overview(monthStart(now));
        return new DashboardOverviewResponse(
                workflow.totalBriefs(),
                workflow.waitingForReview(),
                workflow.needingImprovement(),
                workflow.completedThisMonth(),
                workflow.pipeline(),
                metrics.needsAttention(now.minus(STALE_AFTER)),
                modules.overdueEquipmentReturns(),
                modules.recentActivity()
        );
    }

    private Instant monthStart(Instant now) {
        return ZonedDateTime.ofInstant(now, clock.getZone())
                .withDayOfMonth(1)
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant();
    }

    private record CachedOverview(DashboardOverviewResponse response, Instant expiresAt) {
    }
}
