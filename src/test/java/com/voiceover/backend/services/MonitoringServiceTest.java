package com.voiceover.backend.services;

import com.voiceover.backend.entities.Poi;
import com.voiceover.backend.entities.Route;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MonitoringServiceTest {
    @Test void dashboardAggregatesRealUsageAndPipelineCounters() {
        var jdbc = mock(JdbcTemplate.class);
        var pois = mock(PoiRepository.class);
        var routes = mock(RouteRepository.class);
        var tasks = mock(PipelineTaskRepository.class);
        var usage = mock(UsageEventRepository.class);
        var logs = mock(SystemLogRepository.class);
        var requests = mock(ApiRequestMetrics.class);
        when(requests.snapshot()).thenReturn(new ApiRequestMetrics.Snapshot(20, 1, 42.5, 5.0));
        when(tasks.countByStatus("PENDING")).thenReturn(3L);
        when(tasks.countByStatus("PROCESSING")).thenReturn(1L);
        when(tasks.countByStatus("FAILED")).thenReturn(1L);
        when(logs.findTop10BySeverityInAndOccurredAtAfterOrderByOccurredAtDesc(anyList(), any())).thenReturn(List.of());
        when(pois.findByStatus("PUBLISHED")).thenReturn(List.of());
        when(pois.findAll()).thenReturn(List.of(Poi.builder().id(1L).name("Museum").build()));
        when(routes.findAll()).thenReturn(List.of(Route.builder().id(7L).name("City route").build()));
        when(usage.countActiveUsers(any())).thenReturn(2L);
        when(usage.countRecentListensByPoi(any())).thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));
        when(usage.countRecentListensByLanguage(any())).thenReturn(List.<Object[]>of(new Object[]{"vi", 3L}, new Object[]{"en", 2L}));
        when(usage.countRecentListensByTrigger(any())).thenReturn(List.<Object[]>of(new Object[]{"AUTO_PLAY", 3L}, new Object[]{"TAP_POI", 2L}));
        when(usage.countRecentRouteSelections(any())).thenReturn(List.<Object[]>of(new Object[]{7L, 2L}));

        Map<String, Object> result = new MonitoringService(jdbc, pois, routes, tasks, usage, logs, requests).dashboard();
        assertEquals("WARNING", result.get("applicationStatus"));
        assertEquals("WARNING", result.get("pipelineStatus"));
        assertEquals(3L, result.get("pipelinePending"));
        assertEquals(2L, result.get("activeUsers"));
        assertEquals(3L, result.get("autoListens"));
        assertEquals(2L, result.get("tapListens"));
        assertEquals(60.0, result.get("autoRatioPercent"));
        assertEquals(5L, ((Map<?, ?>) ((List<?>) result.get("poiUsage")).getFirst()).get("listens"));
        assertEquals(2L, ((Map<?, ?>) ((List<?>) result.get("popularRoutes")).getFirst()).get("selections"));
    }
}
