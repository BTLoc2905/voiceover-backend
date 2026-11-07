package com.voiceover.backend.services;

import com.voiceover.backend.entities.SystemLog;
import com.voiceover.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MonitoringService {
    private final JdbcTemplate jdbc;
    private final PoiRepository pois;
    private final RouteRepository routes;
    private final PipelineTaskRepository tasks;
    private final UsageEventRepository usage;
    private final SystemLogRepository logs;
    private final ApiRequestMetrics requests;

    public Map<String, Object> dashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime day = now.minusHours(24);
        var measured = requests.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", now.toString());
        result.put("usageWindowHours", 24);
        result.put("activeWindowMinutes", 2);
        result.put("requestWindowMinutes", 5);
        result.put("applicationStatus", measured.errorRatePercent() == null ? "NORMAL"
                : measured.errorRatePercent() >= 10 ? "ERROR" : measured.errorRatePercent() > 0 ? "WARNING" : "NORMAL");
        result.put("requestCount", measured.requests());
        result.put("errorRatePercent", measured.errorRatePercent());
        result.put("averageLatencyMs", measured.averageLatencyMs());
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            result.put("databaseStatus", "NORMAL");
        } catch (RuntimeException error) {
            result.put("databaseStatus", "ERROR");
            result.put("dataUnavailable", true);
            return result;
        }

        long pending = tasks.countByStatus("PENDING");
        long processing = tasks.countByStatus("PROCESSING");
        long failed = tasks.countByStatus("FAILED");
        result.put("pipelineStatus", failed >= 5 ? "ERROR" : failed > 0 ? "WARNING" : "NORMAL");
        result.put("pipelinePending", pending);
        result.put("pipelineProcessing", processing);
        result.put("pipelineFailed", failed);
        result.put("recentErrors", logs.findTop10BySeverityInAndOccurredAtAfterOrderByOccurredAtDesc(List.of("ERROR", "CRITICAL"), day)
                .stream().map(this::logView).toList());

        result.put("poiCount", pois.count());
        result.put("activePoiCount", pois.findByStatus("PUBLISHED").size());
        result.put("userCount", usage.countDistinctUsers());
        result.put("activeUsers", usage.countActiveUsers(now.minusMinutes(2)));

        Map<Long, Long> listensByPoi = countsById(usage.countRecentListensByPoi(day));
        result.put("poiUsage", pois.findAll().stream().map(poi -> Map.<String, Object>of(
                        "poiId", poi.getId(), "poiName", poi.getName(), "listens", listensByPoi.getOrDefault(poi.getId(), 0L)))
                .sorted((a, b) -> Long.compare((Long) b.get("listens"), (Long) a.get("listens"))).toList());
        result.put("languageUsage", usage.countRecentListensByLanguage(day).stream().map(row -> Map.of(
                "languageCode", row[0], "listens", row[1])).toList());
        Map<String, Long> byTrigger = new HashMap<>();
        for (Object[] row : usage.countRecentListensByTrigger(day)) byTrigger.put((String) row[0], ((Number) row[1]).longValue());
        long auto = byTrigger.getOrDefault("AUTO_PLAY", 0L);
        long tap = byTrigger.getOrDefault("TAP_POI", 0L);
        result.put("autoListens", auto);
        result.put("tapListens", tap);
        result.put("autoRatioPercent", auto + tap == 0 ? null : Math.round(auto * 1000.0 / (auto + tap)) / 10.0);
        Map<Long, Long> routeCounts = countsById(usage.countRecentRouteSelections(day));
        result.put("popularRoutes", routes.findAll().stream().filter(route -> routeCounts.containsKey(route.getId()))
                .map(route -> Map.<String, Object>of("routeId", route.getId(), "routeName", route.getName(), "selections", routeCounts.get(route.getId())))
                .sorted((a, b) -> Long.compare((Long) b.get("selections"), (Long) a.get("selections"))).toList());
        return result;
    }

    private Map<String, Object> logView(SystemLog log) {
        return Map.of("id", log.getId(), "component", log.getComponent(), "severity", log.getSeverity(),
                "message", log.getMessage(), "occurredAt", log.getOccurredAt().toString());
    }

    private Map<Long, Long> countsById(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        return counts;
    }
}
