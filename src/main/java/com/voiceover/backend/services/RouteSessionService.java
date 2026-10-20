package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RouteSessionService {

    private final SessionRepository sessionRepo;
    private final RouteRepository routeRepo;
    private final RouteStopRepository routeStopRepo;
    private final PoiRepository poiRepo;

    // UC03 & UC10: Kiểm tra tính hợp lệ (2-10 POI, tất cả phải PUBLISHED) và tính toán lại quãng đường/thời gian
    public Map<String, Object> validateAndCalculateCustomRoute(List<Long> poiIds) {
        if (poiIds == null || poiIds.size() < 2 || poiIds.size() > 10) {
            throw new IllegalArgumentException("Số lượng POI trong lộ trình không hợp lệ: Phải có từ 2 đến 10 POI (UC03-E1).");
        }

        List<Poi> orderedPois = new ArrayList<>();
        for (Long id : poiIds) {
            Poi p = poiRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("POI ID " + id + " không tồn tại."));
            if (!"PUBLISHED".equals(p.getStatus())) {
                throw new IllegalArgumentException("POI '" + p.getName() + "' chưa được xuất bản, không thể đưa vào lộ trình (UC03-E3 / UC10-E2).");
            }
            orderedPois.add(p);
        }

        double totalDistanceMeters = 0;
        for (int i = 0; i < orderedPois.size() - 1; i++) {
            Poi a = orderedPois.get(i);
            Poi b = orderedPois.get(i + 1);
            totalDistanceMeters += PoiService.calculateHaversineMeters(
                    a.getLatitude().doubleValue(),
                    a.getLongitude().doubleValue(),
                    b.getLatitude().doubleValue(),
                    b.getLongitude().doubleValue()
            );
        }

        // Tốc độ đi bộ trung bình ~ 1.25 m/s (4.5 km/h)
        int estimatedMinutes = (int) Math.ceil((totalDistanceMeters / 1.25) / 60.0);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stopCount", orderedPois.size());
        summary.put("totalDistanceMeters", Math.round(totalDistanceMeters));
        summary.put("estimatedDurationMinutes", estimatedMinutes);
        summary.put("pois", orderedPois);
        return summary;
    }

    @Transactional
    public Route saveRouteWithStops(Long sessionId, String routeName, String status, List<Long> poiSequence) {
        validateAndCalculateCustomRoute(poiSequence);
        Route route = routeRepo.save(Route.builder()
                .sessionId(sessionId)
                .name(routeName)
                .status(status != null ? status : "DRAFT")
                .build());

        routeStopRepo.deleteByRouteId(route.getId());
        for (int i = 0; i < poiSequence.size(); i++) {
            routeStopRepo.save(RouteStop.builder()
                    .routeId(route.getId())
                    .poiId(poiSequence.get(i))
                    .stopOrder(i + 1)
                    .build());
        }
        return route;
    }
}