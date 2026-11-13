package org.unipod.unipoderpbackend.service.dashboard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.ActivityFeed;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.EquipmentReturns;
import org.unipod.unipoderpbackend.repository.dashboard.PipelineMetricsRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void cachesResultsPerPrincipalForThirtySeconds() {
        PipelineMetricsRepository metrics = mock(PipelineMetricsRepository.class);
        DashboardModuleProvider modules = mock(DashboardModuleProvider.class);
        MutableClock clock = new MutableClock(NOW);
        when(metrics.overview(any())).thenReturn(workflowMetrics());
        when(metrics.needsAttention(any())).thenReturn(List.of());
        when(modules.overdueEquipmentReturns()).thenReturn(equipmentReturns());
        when(modules.recentActivity()).thenReturn(activityFeed());
        DashboardService service = new DashboardService(metrics, modules, clock);
        Authentication administrator = authentication("admin", "ROLE_ADMINISTRATOR");
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");

        DashboardOverviewResponse administratorResult = service.overview(administrator);
        assertSame(administratorResult, service.overview(administrator));

        DashboardOverviewResponse coordinatorResult = service.overview(coordinator);
        assertSame(coordinatorResult, service.overview(coordinator));
        verify(metrics, times(2)).overview(any());

        clock.advance(Duration.ofSeconds(31));
        service.overview(administrator);
        verify(metrics, times(3)).overview(any());
    }

    @Test
    void loadsMonthToDateCompletionAndFourteenDayStaleThreshold() {
        PipelineMetricsRepository metrics = mock(PipelineMetricsRepository.class);
        DashboardModuleProvider modules = mock(DashboardModuleProvider.class);
        when(metrics.overview(any())).thenReturn(workflowMetrics());
        when(metrics.needsAttention(any())).thenReturn(List.of());
        when(modules.overdueEquipmentReturns()).thenReturn(equipmentReturns());
        when(modules.recentActivity()).thenReturn(activityFeed());
        DashboardService service = new DashboardService(
                metrics, modules, Clock.fixed(NOW, ZoneOffset.UTC));

        DashboardOverviewResponse result = service.overview(
                authentication("admin", "ROLE_ADMINISTRATOR"));

        ArgumentCaptor<Instant> monthStart = ArgumentCaptor.forClass(Instant.class);
        verify(metrics).overview(monthStart.capture());
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), monthStart.getValue());

        ArgumentCaptor<Instant> staleBefore = ArgumentCaptor.forClass(Instant.class);
        verify(metrics).needsAttention(staleBefore.capture());
        assertEquals(NOW.minus(Duration.ofDays(14)), staleBefore.getValue());
        assertEquals("NOT_CONFIGURED", result.overdueEquipmentReturns().availability());
        assertEquals("NOT_CONFIGURED", result.recentActivity().availability());
    }

    private static DashboardOverviewResponse workflowMetrics() {
        return new DashboardOverviewResponse(
                3, 1, 2, 4, Map.of(), List.of(), null, null);
    }

    private static EquipmentReturns equipmentReturns() {
        return new EquipmentReturns("NOT_CONFIGURED", "Fidel is not configured.", null);
    }

    private static ActivityFeed activityFeed() {
        return new ActivityFeed("NOT_CONFIGURED", "David is not configured.", List.of());
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
