package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiService {
    private final PoiRepository poiRepo;
    private final TranslationRepository translationRepo;
    private final AudioRepository audioRepo;
    private final LanguageRepository languageRepo;
    private final RouteStopRepository routeStopRepo;
    private final PipelineService pipelineService;
    private final OpenStreetMapPoiService openStreetMapPoiService;
    private final WikimediaImageService wikimediaImageService;

    // UC01: Tìm các POI đã xuất bản trong bán kính 500m tính từ vị trí GPS hiện tại
    public List<Map<String, Object>> getNearbyPublishedPois(double userLat, double userLng, double maxRadiusMeters) {
        List<Poi> publishedPois = poiRepo.findByStatus("PUBLISHED");
        List<Map<String, Object>> result = new ArrayList<>();

        for (Poi poi : publishedPois) {
            refreshFallbackNarration(poi);
            ensureThumbnail(poi);
            double distanceMeters = calculateHaversineMeters(
                    userLat,
                    userLng,
                    poi.getLatitude().doubleValue(),
                    poi.getLongitude().doubleValue());
            if ("PUBLISHED".equals(poi.getStatus())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("poi", poi);
                item.put("distanceMeters", Math.round(distanceMeters * 10.0) / 10.0);
                item.put("withinTriggerZone", distanceMeters <= poi.getRadiusM());
                item.put("preloadAudio", distanceMeters <= 300.0); // Quy tắc UC02: Tải trước audio trong bán kính 300m
                result.add(item);
            }

        }
        if (result.size() < 4) {
            try {
                Set<String> existingNames = result.stream()
                        .map(item -> ((Poi) item.get("poi")).getName())
                        .collect(Collectors.toSet());
                for (Poi candidate : openStreetMapPoiService.createSaigonUniversityFallback(
                        userLat, userLng, maxRadiusMeters)) {
                    Poi saved = poiRepo.findByName(candidate.getName()).orElseGet(() -> poiRepo.save(candidate));
                    if (existingNames.contains(saved.getName())) {
                        continue;
                    }
                    double distanceMeters = calculateHaversineMeters(
                            userLat, userLng, saved.getLatitude().doubleValue(), saved.getLongitude().doubleValue());
                    ensureThumbnail(saved);
                    pipelineService.triggerFullPipelineForPoi(saved);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("poi", saved);
                    item.put("distanceMeters", Math.round(distanceMeters * 10.0) / 10.0);
                    item.put("withinTriggerZone", distanceMeters <= saved.getRadiusM());
                    item.put("preloadAudio", distanceMeters <= 300.0);
                    result.add(item);
                }
            } catch (IllegalStateException exception) {
                // OpenStreetMap is an optional discovery source; existing database results remain valid.
                log.warn("Không lấy được POI mới từ OpenStreetMap: {}", exception.getMessage());
            }
        }
        result.sort(Comparator.comparingDouble(m -> (Double) m.get("distanceMeters")));
        return result;
    }

    private void ensureThumbnail(Poi poi) {
        if (poi.getThumbnailUrl() != null && !poi.getThumbnailUrl().isBlank()
                && !wikimediaImageService.hasDirectImage(poi.getName())) {
            return;
        }
        String imageUrl = wikimediaImageService.findImageUrl(poi.getName());
        if (imageUrl == null || imageUrl.isBlank()
                || imageUrl.equals(poi.getThumbnailUrl())) {
            return;
        }
        poi.setThumbnailUrl(imageUrl);
        poiRepo.save(poi);
    }

    private void refreshFallbackNarration(Poi poi) {
        String narration = switch (poi.getName()) {
            case "Đại học Sài Gòn" -> OpenStreetMapPoiService.SAIGON_UNIVERSITY_NARRATION;
            case "Công viên 23 tháng 9" -> OpenStreetMapPoiService.SEPTEMBER_23_PARK_NARRATION;
            case "Chợ An Đông" -> OpenStreetMapPoiService.AN_DONG_MARKET_NARRATION;
            case "Nhà thờ Chợ Quán" -> OpenStreetMapPoiService.CHO_QUAN_CHURCH_NARRATION;
            default -> null;
        };
        if (narration == null || narration.equals(poi.getDescriptionSource())) {
            return;
        }
        poi.setDescriptionSource(narration);
        Poi saved = poiRepo.save(poi);
        pipelineService.triggerFullPipelineForPoi(saved);
    }

    // UC02: Lấy thuyết minh và audio theo ngôn ngữ, tự động fallback về ngôn ngữ mặc định nếu thiếu (UC02-E3)
    public Map<String, Object> getPoiAudioGuide(Long poiId, String requestedLang) {
        Poi poi = poiRepo.findById(poiId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại"));
        if (!"PUBLISHED".equalsIgnoreCase(poi.getStatus())) throw new IllegalArgumentException("POI này chưa xuất bản hoặc đã tạm ẩn.");
        String defaultLang = languageRepo.findByIsDefaultTrue().map(Language::getCode).orElse("vi");

        boolean fallbackUsed = false;
        String activeLang = requestedLang;

        Optional<Translation> transOpt = translationRepo.findByPoiIdAndLanguageCode(poiId, requestedLang);
        String builtInNarration = OpenStreetMapPoiService.localizedNarrationFor(poi.getName(), requestedLang);
        if (builtInNarration != null) {
            transOpt = Optional.of(Translation.builder()
                    .poiId(poi.getId())
                    .languageCode(requestedLang)
                    .content(builtInNarration)
                    .status("COMPLETED")
                    .build());
            activeLang = requestedLang;
        } else if (transOpt.isEmpty() || !"COMPLETED".equals(transOpt.get().getStatus())) {
            if (transOpt.isEmpty() || !"PROCESSING".equals(transOpt.get().getStatus())) {
                pipelineService.triggerFullPipelineForPoi(poi);
            }
            transOpt = translationRepo.findByPoiIdAndLanguageCode(poiId, defaultLang);
            activeLang = defaultLang;
            fallbackUsed = true;
        }

        Optional<Audio> audioOpt = builtInNarration != null
                ? Optional.empty()
                : audioRepo.findByPoiIdAndLanguageCode(poiId, activeLang);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("poiId", poi.getId());
        response.put("poiName", poi.getName());
        response.put("thumbnailUrl", poi.getThumbnailUrl());
        response.put("requestedLanguage", requestedLang);
        response.put("activeLanguage", activeLang);
        response.put("fallbackUsed", fallbackUsed);
        response.put("textContent", transOpt.map(Translation::getContent).orElse(poi.getDescriptionSource()));
        response.put("audioUrl", audioOpt.map(Audio::getFilePath).orElse(null));
        response.put("audioStatus", audioOpt.map(Audio::getStatus).orElse("TEXT_ONLY"));
        response.put("browserTtsAllowed", languageRepo.findById(activeLang).map(Language::isTtsSupported).orElse(false));
        return response;
    }

    // UC06 & UC08: Tạo mới hoặc cập nhật POI và kích hoạt Pipeline tự động
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Poi saveOrUpdatePoi(Poi poiRequest) {
        if (poiRequest.getId() != null) {
            poiRepo.findForUpdate(poiRequest.getId()).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
            if (!RouteSessionService.published(poiRequest.getStatus()) || !RouteSessionService.hasValidCoordinates(poiRequest)) rejectServingRoutes(poiRequest.getId());
        }
        if (poiRequest.getSourceLanguageCode() == null || poiRequest.getSourceLanguageCode().isBlank()) {
            poiRequest.setSourceLanguageCode("vi");
        }
        if (poiRequest.getRadiusM() == null || poiRequest.getRadiusM() <= 0) {
            poiRequest.setRadiusM(50);
        }
        Poi saved = poiRepo.save(poiRequest);
        pipelineService.triggerFullPipelineForPoi(saved);
        return saved;
    }

    // UC06 & Quy tắc F-05: Chặn xóa POI nếu đang nằm trong Route/Session đã xuất bản
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deletePoiSafely(Long poiId) {
        poiRepo.findForUpdate(poiId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
        rejectServingRoutes(poiId);
        List<Route> references = routeStopRepo.findReferencingRoutes(poiId);
        if (!references.isEmpty()) throw new RouteConflictException("Hãy bỏ POI khỏi các Route trước khi xóa: " + routeNames(references));
        poiRepo.deleteById(poiId);
    }

    private void rejectServingRoutes(Long poiId) {
        List<Route> affected = routeStopRepo.findServingRoutes(poiId);
        if (!affected.isEmpty()) throw new RouteConflictException("Không thể xóa, ẩn/chuyển nháp hoặc làm mất tọa độ hợp lệ của POI đang phục vụ du khách. Route bị ảnh hưởng: " + routeNames(affected));
    }

    private String routeNames(List<Route> routes) {
        return routes.stream().map(route -> "'" + route.getName() + "' (#" + route.getId() + ")").collect(Collectors.joining(", "));
    }

    // UC07: Gợi ý bản nháp mô tả gốc bằng AI (Không tự động lưu vào CSDL)
    public Map<String, String> generateAiDescriptionDraft(String poiName, String notes) {
        if (poiName == null || poiName.isBlank()) {
            throw new IllegalArgumentException("Tên POI là bắt buộc để AI gợi ý mô tả (UC07).");
        }
        String draft = String.format(
            "%s là một điểm tham quan văn hóa - lịch sử tiêu biểu. %s " +
            "Tại đây, du khách sẽ được chiêm ngưỡng kiến trúc độc đáo và lắng nghe những câu chuyện lịch sử sống động gắn liền với địa danh này.",
            poiName.trim(),
            (notes != null && !notes.isBlank()) ? "Đặc điểm nổi bật: " + notes.trim() + "." : ""
        );
        return Map.of(
            "poiName", poiName,
            "draftDescription", draft,
            "notice", "Bản nháp do AI tạo. Vui lòng kiểm tra và nhấn 'Lưu' để chính thức áp dụng và kích hoạt Pipeline (UC07)."
        );
    }

    public static double calculateHaversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Bán kính Trái Đất (m)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
