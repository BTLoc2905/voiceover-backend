package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routeRepo;
    private final RouteStopRepository routeStopRepo;
    private final PoiRepository poiRepo;
    private final WalkingDirectionsService walkingDirectionsService;
    private final AudioRepository audioRepo;
    private final TranslationRepository translationRepo;
    private final LanguageRepository languageRepo;

    // UC03 & UC09: Kiểm tra tính hợp lệ (2-10 POI, tất cả phải PUBLISHED) và tính toán lại quãng đường/thời gian
    public Map<String, Object> validateAndCalculateCustomRoute(List<Long> poiIds) {
        List<Poi> orderedPois = validateStops(poiIds);
        WalkingDirectionsService.WalkingRoute walkingRoute = walkingDirectionsService.calculate(orderedPois);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stopCount", orderedPois.size());
        summary.put("totalDistanceMeters", walkingRoute.distanceMeters());
        summary.put("estimatedDurationMinutes", (int) Math.ceil(walkingRoute.durationSeconds() / 60.0));
        summary.put("durationSeconds", walkingRoute.durationSeconds());
        summary.put("geometry", walkingRoute.geometry());
        summary.put("legs", walkingRoute.legs());
        summary.put("travelMode", "WALKING");
        summary.put("pois", orderedPois);
        return summary;
    }

    List<Poi> validateStops(List<Long> poiIds) {
        if (poiIds == null || poiIds.size() < 2 || poiIds.size() > 10) {
            throw new IllegalArgumentException("Số lượng POI trong lộ trình không hợp lệ: Phải có từ 2 đến 10 POI (UC03-E1).");
        }

        if (poiIds.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(poiIds).size() != poiIds.size()) {
            throw new IllegalArgumentException("Danh sách POI không hợp lệ hoặc có điểm tham quan trùng nhau.");
        }
        List<Poi> orderedPois = new ArrayList<>();
        for (Long id : poiIds) {
            Poi p = poiRepo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("POI ID " + id + " không tồn tại."));
            if (!published(p.getStatus())) {
                throw new IllegalArgumentException("POI '" + p.getName() + "' chưa được xuất bản, không thể đưa vào lộ trình (UC03-E3 / UC09-E2).");
            }
            if (!hasValidCoordinates(p)) {
                throw new IllegalArgumentException("POI '" + p.getName() + "' có tọa độ không hợp lệ (UC03-E3).");
            }
            orderedPois.add(p);
        }

        return orderedPois;
    }

    static boolean hasValidCoordinates(Poi poi) {
        return poi.getLatitude() != null && poi.getLongitude() != null
                && Math.abs(poi.getLatitude().doubleValue()) <= 90
                && Math.abs(poi.getLongitude().doubleValue()) <= 180;
    }

    public List<Poi> getPublishedRoutePois() {
        return poiRepo.findByStatus("PUBLISHED").stream().filter(RouteService::hasValidCoordinates).toList();
    }

    /** Only published, valid routes are visible; legacy Tour Sessions are never consulted. */
    public List<Map<String, Object>> getSuggestedRoutes() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Route route : routeRepo.findByStatus("PUBLISHED")) {
            try {
                nameOf(route.getName(), true);
                List<Poi> stops = validateStops(idsOf(route.getId()));
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", route.getId()); view.put("name", route.getName());
                view.put("description", route.getDescription());
                view.put("stopCount", stops.size()); view.put("pois", stops);
                result.add(view);
            } catch (IllegalArgumentException ignored) {
                // Invalid/hidden POIs also make a legacy published route unavailable.
            }
        }
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Route createRoute(String name, String description, String status, List<Long> poiIds) {
        return writeRoute(null, name, description, status, poiIds);
    }
    static boolean published(String status) { return "PUBLISHED".equalsIgnoreCase(status); }

    private String statusOf(String value) {
        String status = value == null ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "PUBLISHED").contains(status)) throw new IllegalArgumentException("Trạng thái phải là DRAFT hoặc PUBLISHED. Tạm đóng bằng chuyển về Nháp.");
        return status;
    }

    private String nameOf(String value, boolean publishing) {
        String name = value == null ? "" : value.trim();
        if (name.length() > 255) throw new IllegalArgumentException("Tên không được dài quá 255 ký tự.");
        if (publishing && name.isBlank()) throw new IllegalArgumentException("Cần nhập tên trước khi xuất bản.");
        return name;
    }

    private List<Long> idsOf(Long routeId) {
        return routeStopRepo.findByRouteIdOrderByStopOrderAsc(routeId).stream().map(RouteStop::getPoiId).toList();
    }

    // Publication writes and POI changes lock the same POI rows in ascending order.
    private void lockPois(List<Long> ids) {
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) throw new IllegalArgumentException("Mã POI không hợp lệ.");
        ids.stream().distinct().sorted().forEach(id -> poiRepo.findForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("POI không tồn tại: " + id)));
    }

    public List<Map<String, Object>> listRoutes() {
        return routeRepo.findAll().stream().sorted(Comparator.comparing(Route::getId).reversed())
                .map(this::routeView).toList();
    }

    public Map<String, Object> getRoute(Long id) {
        return routeView(routeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Route không tồn tại: " + id)));
    }
    public Map<String, Object> routeView(Route route) {
        List<Long> ids = idsOf(route.getId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", route.getId()); view.put("description", route.getDescription());
        view.put("name", route.getName()); view.put("status", route.getStatus().toUpperCase(Locale.ROOT));
        view.put("poiIds", ids); view.put("stopCount", ids.size());
        List<String> issues = new ArrayList<>();
        if (route.getName() == null || route.getName().isBlank()) issues.add("Chưa có tên Route.");
        try { validateStops(ids); } catch (IllegalArgumentException error) { issues.add(error.getMessage()); }
        view.put("publishIssues", issues);
        return view;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteRoute(Long routeId) {
        Route route = routeRepo.findForUpdate(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route không tồn tại: " + routeId));
        routeStopRepo.deleteByRouteId(routeId);
        routeStopRepo.flush();
        routeRepo.delete(route);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Route updateRoute(Long routeId, String name, String description, String status, List<Long> poiIds) {
        return writeRoute(routeId, name, description, status, poiIds);
    }

    private Route writeRoute(Long routeId, String name, String description, String requestedStatus, List<Long> requestedIds) {
        String status = statusOf(requestedStatus);
        String cleanName = nameOf(name, published(status));
        if (description != null && description.length() > 10000)
            throw new IllegalArgumentException("Mô tả Route tối đa 10.000 ký tự.");
        List<Long> ids = requestedIds == null ? List.of() : requestedIds;
        Route route = routeId == null ? new Route() : routeRepo.findForUpdate(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Route không tồn tại: " + routeId));
        lockPois(ids);
        if (published(status)) validateStops(ids);
        route.setName(cleanName); route.setDescription(description == null ? "" : description.strip()); route.setStatus(status);
        route = routeRepo.saveAndFlush(route);
        routeStopRepo.deleteByRouteId(route.getId());
        routeStopRepo.flush(); // Release unique(route_id, stop_order) before inserting the new sequence.
        for (int i = 0; i < ids.size(); i++) routeStopRepo.save(RouteStop.builder()
                .routeId(route.getId()).poiId(ids.get(i)).stopOrder(i + 1).build());
        routeStopRepo.flush();
        return route;
    }
    public Map<String, Object> poiCoverage(Long poiId) {
        Poi poi = poiRepo.findById(poiId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
        List<Map<String, Object>> languages = languageRepo.findByIsActiveTrue().stream().map(language -> {
            var audio = audioRepo.findByPoiIdAndLanguageCode(poiId, language.getCode());
            boolean text = translationRepo.findByPoiIdAndLanguageCode(poiId, language.getCode())
                    .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()) && t.getContent() != null && !t.getContent().isBlank()).isPresent();
            boolean sourceText = language.getCode().equals(poi.getSourceLanguageCode()) && poi.getDescriptionSource() != null && !poi.getDescriptionSource().isBlank();
            boolean ready = audio.filter(a -> "COMPLETED".equalsIgnoreCase(a.getStatus()) && a.getFilePath() != null && !a.getFilePath().isBlank()).isPresent();
            return Map.<String, Object>of("code", language.getCode(), "name", language.getName(), "audioReady", ready,
                    "textReady", text || sourceText, "ttsSupported", language.isTtsSupported(), "audioStatus", audio.map(Audio::getStatus).orElse("MISSING"));
        }).toList();
        return Map.of("poiId", poiId, "languages", languages);
    }

    /** UC04: Recheck public availability when the visitor actually starts. */
    public Map<String, Object> startRoute(Long routeId, List<Long> personalPoiIds) {
        Route route = requirePublicRoute(routeId);
        List<Long> selected = personalPoiIds == null ? idsOf(routeId) : personalPoiIds;
        Map<String, Object> result = new LinkedHashMap<>(validateAndCalculateCustomRoute(selected));
        result.put("routeId", routeId); result.put("name", route.getName()); result.put("description", route.getDescription());
        return result;
    }

    private Route requirePublicRoute(Long id) {
        Route route = routeRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Route không tồn tại."));
        if (!published(route.getStatus())) throw new RouteConflictException("Route đã ẩn. Hãy chọn lộ trình khác.");
        nameOf(route.getName(), true);
        validateStops(idsOf(id));
        return route;
    }

    public Map<String, Object> walkingLeg(Long routeId, Long targetId, double lat, double lng) {
        requirePublicRoute(routeId);
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new IllegalArgumentException("Vị trí GPS không hợp lệ.");
        Poi target = poiRepo.findById(targetId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
        if (!published(target.getStatus()) || !hasValidCoordinates(target)) throw new RouteConflictException("Điểm đến không còn khả dụng.");
        Poi origin = Poi.builder().latitude(java.math.BigDecimal.valueOf(lat)).longitude(java.math.BigDecimal.valueOf(lng)).build();
        var leg = walkingDirectionsService.calculate(List.of(origin, target));
        return Map.of("targetId", targetId, "geometry", leg.geometry(), "totalDistanceMeters", leg.distanceMeters(),
                "estimatedDurationMinutes", (int) Math.ceil(leg.durationSeconds() / 60.0), "travelMode", "WALKING");
    }
}
