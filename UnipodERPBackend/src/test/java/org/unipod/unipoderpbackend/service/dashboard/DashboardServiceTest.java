package org.unipod.unipoderpbackend.service.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.FinanceSummary;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.OperationsSummary;
import org.unipod.unipoderpbackend.repository.dashboard.PipelineMetricsRepository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void cachesScopedResultsForThirtySecondsAndSeparatesRestrictedTileVisibility() {
        PipelineMetricsRepository metrics = mock(PipelineMetricsRepository.class);
        DashboardModuleProvider modules = mock(DashboardModuleProvider.class);
        MutableClock clock = new MutableClock(NOW);
        DashboardOverviewResponse metricResult = new DashboardOverviewResponse(
                3, java.util.Map.of(), 1, 2, 0, 0, 0, 0
        );
        when(metrics.overview(any(), any(), any())).thenReturn(metricResult);
        when(metrics.needsAttention(any(), any())).thenReturn(List.of());
        when(modules.finance()).thenReturn(new FinanceSummary(
                "AVAILABLE", null, java.math.BigDecimal.ZERO, List.of()
        ));
        when(modules.operations()).thenReturn(new OperationsSummary(
                "AVAILABLE", null, 0L, 0L, 0L
        ));
        DashboardService service = new DashboardService(metrics, modules, clock);
        Authentication administrator = authentication("admin", "ROLE_ADMINISTRATOR");
        Authentication observer = authentication("observer", "ROLE_OBSERVER");

        DashboardOverviewResponse adminResult = service.overview(administrator);
        assertNotNull(adminResult.finance());
        assertNotNull(adminResult.operations());
        assertSame(adminResult, service.overview(administrator));

        DashboardOverviewResponse observerResult = service.overview(observer);
        assertNull(observerResult.finance());
        assertNull(observerResult.operations());
        assertSame(observerResult, service.overview(observer));
        verify(modules, times(1)).finance();
        verify(modules, times(1)).operations();
        verify(modules).recentActivity("ALL", "admin");
        verify(metrics, times(2)).overview(any(), eq("ALL"), any());

        clock.advance(Duration.ofSeconds(31));
        assertNotNull(service.overview(administrator));
        verify(metrics, times(3)).overview(any(), eq("ALL"), any());
    }

    @Test
    void reviewerDashboardUsesReviewerScopeAndDoesNotRequestFinanceData() {
        PipelineMetricsRepository metrics = mock(PipelineMetricsRepository.class);
        DashboardModuleProvider modules = mock(DashboardModuleProvider.class);
        DashboardOverviewResponse metricResult = new DashboardOverviewResponse(
                0, java.util.Map.of(), 0, 0, 0, 0, 0, 0
        );
        when(metrics.overview(any(), any(), any())).thenReturn(metricResult);
        when(metrics.needsAttention(any(), any())).thenReturn(List.of());
        DashboardService service = new DashboardService(metrics, modules, Clock.fixed(NOW, ZoneOffset.UTC));

        assertNull(service.overview(authentication("reviewer-1", "ROLE_REVIEWER")).finance());
        assertNull(service.overview(authentication("reviewer-1", "ROLE_REVIEWER")).operations());

        verify(metrics).overview(any(), eq("REVIEWER"), eq("reviewer-1"));
        verify(modules, never()).finance();
        verify(modules, never()).operations();
        verify(modules).recentActivity("REVIEWER", "reviewer-1");
    }

    private static Authentication authentication(String name, String role) {
        return new UsernamePasswordAuthenticationToken(
                name,
                "n/a",
                List.of(new SimpleGrantedAuthority(role))
        );
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }
    }
}
