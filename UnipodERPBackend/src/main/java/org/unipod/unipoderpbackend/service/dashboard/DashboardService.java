package org.unipod.unipoderpbackend.service.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.repository.dashboard.PipelineMetricsRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

@Service
public class DashboardService {
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final PipelineMetricsRepository metrics;
    private final DashboardModuleProvider modules;
    private final Clock clock;
    private final Map<CacheKey, CachedOverview> cache = new ConcurrentHashMap<>();

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
        String scope = SubmissionVisibility.scope(authentication);
        boolean restrictedTilesVisible = DashboardVisibility.canViewRestrictedTiles(authentication);
        String principal = scope.equals("ALL") ? "" : authentication.getName();
        CacheKey key = new CacheKey(scope, principal, restrictedTilesVisible);
        Instant now = clock.instant();
        cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        CachedOverview cached = cache.compute(key, (cacheKey, existing) -> {
            if (existing != null && existing.expiresAt().isAfter(now)) {
                return existing;
            }
            return new CachedOverview(load(authentication, scope, restrictedTilesVisible), now.plus(CACHE_TTL));
        });
        return cached.response();
    }

    private DashboardOverviewResponse load(
            Authentication authentication,
            String scope,
            boolean restrictedTilesVisible
    ) {
        DashboardOverviewResponse metricsResponse = metrics.overview(
                clock.instant().minus(Duration.ofDays(14)),
                scope,
                authentication.getName()
        );
        return new DashboardOverviewResponse(
                metricsResponse.totalSubmissions(),
                metricsResponse.byStage(),
                metricsResponse.studentSubmissions(),
                metricsResponse.generalSubmissions(),
                metricsResponse.awaitingActionCount(),
                metricsResponse.forwardedCount(),
                metricsResponse.completedCount(),
                metricsResponse.recentlyUpdatedCount(),
                metricsResponse.awaitingReviewCount(),
                metricsResponse.refinementRequestedCount(),
                metricsResponse.pipelineAtAGlance(),
                metrics.needsAttention(scope, authentication.getName()),
                restrictedTilesVisible ? modules.operations() : null,
                restrictedTilesVisible ? modules.finance() : null,
                modules.recentActivity(scope, authentication.getName())
        );
    }

    private record CacheKey(String scope, String principal, boolean restrictedTilesVisible) {
    }

    private record CachedOverview(DashboardOverviewResponse response, Instant expiresAt) {
    }
}
