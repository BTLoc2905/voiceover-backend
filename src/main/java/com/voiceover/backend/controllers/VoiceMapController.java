package com.voiceover.backend.controllers;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import com.voiceover.backend.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.security.crypto.password.PasswordEncoder;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VoiceMapController {

    private final PoiService poiService;
    private final PipelineService pipelineService;
    private final RouteSessionService routeSessionService;
    private final PoiRepository poiRepo;
    private final LanguageRepository languageRepo;
    private final TranslationRepository translationRepo;
    private final AudioRepository audioRepo;
    private final PipelineTaskRepository pipelineTaskRepo;
    private final UsageEventRepository usageEventRepo;
    private final SystemLogRepository systemLogRepo;
    private final RealContentService realContentService;
    private final AdminAccountRepository adminAccountRepo;
    private final PasswordEncoder passwordEncoder;

    // ==================== API DU KHÁCH (KHÔNG CẦN ĐĂNG NHẬP - UC01..UC04) ====================

    // UC01: Định vị GPS và lấy danh sách POI đã xuất bản trong bán kính 500m
    @GetMapping("/public/pois/nearby")
    public ResponseEntity<?> getNearbyPois(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") double radiusM) {
        return ResponseEntity.ok(poiService.getNearbyPublishedPois(lat, lng, radiusM));
    }

    // UC02: Nghe thuyết minh POI đa ngôn ngữ (tự động fallback nếu thiếu audio)
    @GetMapping("/public/pois/{poiId}/guide")
    public ResponseEntity<?> getPoiGuide(
            @PathVariable Long poiId,
            @RequestParam(defaultValue = "vi") String lang,
            @RequestParam(defaultValue = "TAP_POI") String triggerType,
            @RequestParam(defaultValue = "anon-guest") String clientId) {
        // GET includes preload/retry. PlaybackEventController records actual start/end separately.
        return ResponseEntity.ok(poiService.getPoiAudioGuide(poiId, lang));
    }

    // UC03: Tùy chỉnh lộ trình cá nhân; xem và bắt đầu dẫn đường thuộc UC04.
    @PostMapping("/public/routes/customize")
    public ResponseEntity<?> customizePersonalRoute(@RequestBody List<Long> poiIds) {
        try {
            return ResponseEntity.ok(routeSessionService.validateAndCalculateCustomRoute(poiIds));
        } catch (WalkingDirectionsService.NoWalkingRouteException exception) {
            return ResponseEntity.status(422).body(Map.of("code", "NO_WALKING_ROUTE", "message", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ROUTE", "message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(503).body(Map.of("code", "ROUTING_UNAVAILABLE", "message", exception.getMessage()));
        }
    }

    @GetMapping("/public/routes")
    public ResponseEntity<?> getSuggestedRoutes() {
        return ResponseEntity.ok(routeSessionService.getSuggestedRoutes());
    }

    @GetMapping("/public/pois")
    public ResponseEntity<?> getRoutePoiCatalog() {
        return ResponseEntity.ok(routeSessionService.getPublishedRoutePois());
    }

    @GetMapping("/public/languages")
    public ResponseEntity<?> getActiveLanguages() {
        return ResponseEntity.ok(languageRepo.findByIsActiveTrue());
    }

    // ==================== API QUẢN TRỊ VIÊN (ADMIN PORTAL & DASHBOARD - UC05..UC11) ====================

    @PostMapping("/admin/auth/login")
    public ResponseEntity<?> adminLogin(@RequestBody Map<String, String> payload) {
        String email = payload.getOrDefault("email", "").trim().toLowerCase(Locale.ROOT);
        String password = payload.getOrDefault("password", "");
        AdminAccount account = adminAccountRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không đúng."));
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
                || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không đúng, hoặc tài khoản đã bị vô hiệu hóa.");
        }
        return ResponseEntity.ok(Map.of(
                "id", account.getId(),
                "email", account.getEmail(),
                "fullName", account.getFullName(),
                "role", account.getRole()));
    }

    @GetMapping("/admin/pois")
    public ResponseEntity<?> getAdminPois() {
        return ResponseEntity.ok(poiRepo.findAll());
    }

    // UC06 & UC08: Tạo mới / Cập nhật POI và tự động chạy Pipeline 15+ ngôn ngữ
    @PostMapping("/admin/pois")
    public ResponseEntity<?> savePoi(@RequestBody Poi poi) {
        return ResponseEntity.ok(poiService.saveOrUpdatePoi(poi));
    }

    @PostMapping("/admin/pois/with-real-image")
    public ResponseEntity<?> createPoiWithRealImage(@RequestBody Map<String, String> payload) {
        Poi poi = realContentService.createPoiWithRealImage(
                payload.get("name"),
                payload.get("description"),
                new BigDecimal(payload.get("latitude")),
                new BigDecimal(payload.get("longitude")),
                payload.get("radiusM") == null ? 50 : Integer.valueOf(payload.get("radiusM")));
        return ResponseEntity.ok(poiService.saveOrUpdatePoi(poi));
    }

    // UC06: Xóa POI có kiểm tra ràng buộc Route/Session đang xuất bản
    @DeleteMapping("/admin/pois/{poiId}")
    public ResponseEntity<?> deletePoi(@PathVariable Long poiId) {
        poiService.deletePoiSafely(poiId);
        return ResponseEntity.ok(Map.of("message", "Đã xóa POI thành công."));
    }

    // UC10: Tạo và lưu Route kèm thứ tự POI (poi_sequence) trong Session
    @PostMapping(value = "/admin/sessions/{sessionId}/routes", params = "name")
    public ResponseEntity<?> createRoute(
            @PathVariable Long sessionId,
            @RequestParam String name,
            @RequestParam(defaultValue = "DRAFT") String status,
            @RequestBody List<Long> poiSequence) {
        return ResponseEntity.ok(routeSessionService.saveRouteWithStops(sessionId, name, status, poiSequence));
    }

    // UC11: Giám sát hệ thống và thống kê sử dụng thực tế (Cập nhật <= 10s, quy ước Xanh/Vàng/Đỏ)
    @GetMapping("/admin/monitoring/dashboard")
    public ResponseEntity<?> getMonitoringDashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("poiCount", poiRepo.count());
        dashboard.put("activePoiCount", poiRepo.findByStatus("PUBLISHED").size());
        dashboard.put("userCount", usageEventRepo.countDistinctUsers());
        Map<Long, Long> listensByPoiId = new HashMap<>();
        for (Object[] row : usageEventRepo.countListensByPoi()) {
            listensByPoiId.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> poiUsage = poiRepo.findAll().stream()
                .map(poi -> Map.<String, Object>of(
                        "poiId", poi.getId(),
                        "poiName", poi.getName(),
                        "listens", listensByPoiId.getOrDefault(poi.getId(), 0L)))
                .sorted((left, right) -> Long.compare(
                        ((Number) right.get("listens")).longValue(),
                        ((Number) left.get("listens")).longValue()))
                .toList();
        dashboard.put("poiUsage", poiUsage);
        return ResponseEntity.ok(dashboard);
    }
}
