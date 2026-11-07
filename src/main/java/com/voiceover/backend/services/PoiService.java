package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Locale;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiService {
    private final PoiRepository poiRepo;
    private final TranslationRepository translationRepo;
    private final AudioRepository audioRepo;
    private final LanguageRepository languageRepo;
    private final RouteStopRepository routeStopRepo;
    private final TranslationReviewService reviewService;
    private final PoiThumbnailService thumbnailService;

    // Public reads never edit source content, import sample POIs or enqueue jobs.
    public List<Map<String, Object>> getNearbyPublishedPois(double userLat, double userLng, double maxRadiusMeters) {
        if (!Double.isFinite(userLat) || !Double.isFinite(userLng) || Math.abs(userLat) > 90 || Math.abs(userLng) > 180
                || !Double.isFinite(maxRadiusMeters) || maxRadiusMeters <= 0)
            throw new IllegalArgumentException("Vị trí hoặc bán kính tìm kiếm không hợp lệ.");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Poi poi : poiRepo.findByStatus("PUBLISHED")) {
            if (!RouteSessionService.hasValidCoordinates(poi)) continue;
            double distance = calculateHaversineMeters(userLat, userLng, poi.getLatitude().doubleValue(), poi.getLongitude().doubleValue());
            if (distance > maxRadiusMeters) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("poi", poi); item.put("distanceMeters", Math.round(distance * 10.0) / 10.0);
            item.put("withinTriggerZone", distance <= poi.getRadiusM()); item.put("preloadAudio", distance <= 300.0);
            result.add(item);
        }
        result.sort(Comparator.comparingDouble(item -> (Double) item.get("distanceMeters")));
        return result;
    }

    // UC02: Lấy thuyết minh và audio theo ngôn ngữ, tự động fallback về ngôn ngữ mặc định nếu thiếu (UC02-E3)
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> getPoiAudioGuide(Long poiId, String requestedLang) {
        Poi poi = poiRepo.findById(poiId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại"));
        if (!"PUBLISHED".equalsIgnoreCase(poi.getStatus())) throw new IllegalArgumentException("POI này chưa xuất bản hoặc đã tạm ẩn.");
        String defaultLang = languageRepo.findByIsDefaultTrue().map(Language::getCode).orElse("vi");

        String activeLang = requestedLang;
        Optional<Translation> transOpt = completedTranslation(poi, requestedLang);
        String builtInNarration = transOpt.isPresent() ? null : availableBuiltIn(poi, requestedLang);
        if (transOpt.isEmpty() && builtInNarration == null) {
            activeLang = defaultLang;
            transOpt = completedTranslation(poi, defaultLang);
            builtInNarration = transOpt.isPresent() ? null : availableBuiltIn(poi, defaultLang);
        }
        if (transOpt.isEmpty() && builtInNarration == null) activeLang = poi.getSourceLanguageCode();
        boolean fallbackUsed = !Objects.equals(activeLang, requestedLang);
        String textContent = transOpt.map(Translation::getContent).orElse(builtInNarration == null ? poi.getDescriptionSource() : builtInNarration);
        Optional<Audio> audioOpt = transOpt.isPresent() ? audioRepo.findByPoiIdAndLanguageCode(poiId, activeLang) : Optional.empty();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("poiId", poi.getId());
        response.put("contentRevision", poi.getContentRevision());
        response.put("poiName", poi.getName());
        response.put("thumbnailUrl", poi.getThumbnailUrl());
        response.put("requestedLanguage", requestedLang);
        response.put("activeLanguage", activeLang);
        response.put("fallbackUsed", fallbackUsed);
        response.put("textContent", textContent);
        response.put("audioUrl", audioOpt.filter(audio -> "COMPLETED".equals(audio.getStatus())).map(Audio::getFilePath).filter(path -> !path.isBlank()).orElse(null));
        response.put("audioStatus", audioOpt.map(Audio::getStatus).orElse("TEXT_ONLY"));
        response.put("browserTtsAllowed", languageRepo.findById(activeLang).map(Language::isTtsSupported).orElse(false));
        return response;
    }

    private Optional<Translation> completedTranslation(Poi poi, String language) {
        return translationRepo.findByPoiIdAndLanguageCode(poi.getId(), language)
                .filter(text -> (poi.getContentRevision() == 0 || Objects.equals(poi.getSourceFingerprint(), text.getSourceHash())) && "COMPLETED".equals(text.getStatus()) && text.getContent() != null && !text.getContent().isBlank());
    }
    private String availableBuiltIn(Poi poi, String language) {
        return poi.getContentRevision() == 0 && Set.of("Đại học Sài Gòn", "Công viên 23 tháng 9", "Chợ An Đông", "Nhà thờ Chợ Quán").contains(poi.getName())
                ? OpenStreetMapPoiService.localizedNarrationFor(poi.getName(), language) : null;
    }

    // Source, invalidation and durable language tasks are one database transaction.
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Poi saveOrUpdatePoi(Poi request) {
        if (request == null) throw new IllegalArgumentException("Dữ liệu POI không hợp lệ.");
        Poi target;
        if (request.getId() == null) {
            target = request;
            target.setContentRevision(0); target.setSourceFingerprint(null);
        } else {
            target = poiRepo.findForUpdate(request.getId()).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
            if (request.getContentRevision() != target.getContentRevision())
                throw new ContentReviewConflictException("Mô tả nguồn đã thay đổi. Tải lại POI trước khi lưu; nội dung đang soạn chưa bị ghi đè.");
            if (!RouteSessionService.published(request.getStatus()) || !RouteSessionService.hasValidCoordinates(request)) rejectServingRoutes(request.getId());
        }
        validatePoi(request);
        if (RouteSessionService.published(request.getStatus())) reviewService.requireDefaultAudioForPublication(request, target);
        target.setName(request.getName().strip()); target.setDescriptionSource(request.getDescriptionSource().strip());
        target.setSourceLanguageCode(request.getSourceLanguageCode()); target.setStatus(request.getStatus().toUpperCase(Locale.ROOT));
        target.setLatitude(request.getLatitude()); target.setLongitude(request.getLongitude()); target.setRadiusM(request.getRadiusM());
        target.setThumbnailUrl(request.getThumbnailUrl());
        Poi saved = poiRepo.saveAndFlush(target);
        reviewService.enqueueSource(saved);
        return saved;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Poi uploadThumbnail(Long poiId, org.springframework.web.multipart.MultipartFile image) {
        Poi poi = poiRepo.findForUpdate(poiId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
        poi.setThumbnailUrl(thumbnailService.store(poiId, image));
        return poiRepo.save(poi);
    }

    private void validatePoi(Poi poi) {
        if (poi == null) throw new IllegalArgumentException("Dữ liệu POI không hợp lệ.");
        if (poi.getName() == null || poi.getName().isBlank()) throw new IllegalArgumentException("Tên POI không được để trống.");
        if (poi.getName().strip().length() > 255) throw new IllegalArgumentException("Tên POI không được vượt quá 255 ký tự.");
        if (poi.getDescriptionSource() == null || poi.getDescriptionSource().isBlank())
            throw new IllegalArgumentException("Mô tả gốc không được để trống.");
        if (poi.getLatitude() == null || poi.getLatitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || poi.getLatitude().compareTo(BigDecimal.valueOf(90)) > 0)
            throw new IllegalArgumentException("Vĩ độ phải nằm trong khoảng -90 đến 90.");
        if (poi.getLongitude() == null || poi.getLongitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || poi.getLongitude().compareTo(BigDecimal.valueOf(180)) > 0)
            throw new IllegalArgumentException("Kinh độ phải nằm trong khoảng -180 đến 180.");
        if (poi.getLatitude().stripTrailingZeros().scale() > 17 || poi.getLongitude().stripTrailingZeros().scale() > 17)
            throw new IllegalArgumentException("Tọa độ hỗ trợ tối đa 17 chữ số thập phân; hãy giảm độ chính xác trước khi lưu.");
        if (poi.getRadiusM() == null || poi.getRadiusM() <= 0)
            throw new IllegalArgumentException("Bán kính phải là số nguyên lớn hơn 0 m.");
        if (poi.getStatus() == null || !Set.of("DRAFT", "PUBLISHED", "HIDDEN").contains(poi.getStatus().toUpperCase(Locale.ROOT)))
            throw new IllegalArgumentException("Trạng thái POI phải là DRAFT, PUBLISHED hoặc HIDDEN.");
        if (poi.getThumbnailUrl() != null && poi.getThumbnailUrl().length() > 1000)
            throw new IllegalArgumentException("URL thumbnail không được vượt quá 1000 ký tự.");
        if (poi.getThumbnailUrl() != null && !poi.getThumbnailUrl().isBlank()
                && !poi.getThumbnailUrl().startsWith("/images/")
                && !(poi.getThumbnailUrl().startsWith("https://") || poi.getThumbnailUrl().startsWith("http://")))
            throw new IllegalArgumentException("Thumbnail phải là URL HTTP(S) hoặc ảnh đã tải lên trong /images/.");
        if (poi.getSourceLanguageCode() == null || poi.getSourceLanguageCode().isBlank())
            throw new IllegalArgumentException("Hãy chọn ngôn ngữ gốc của mô tả.");
        if (languageRepo.findById(poi.getSourceLanguageCode()).filter(Language::isActive).isEmpty())
            throw new IllegalArgumentException("Ngôn ngữ gốc không tồn tại hoặc đã ngừng hoạt động.");
    }

    // UC06 & Quy tắc F-05: Chặn xóa POI nếu đang nằm trong Route/Session đã xuất bản
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deletePoiSafely(Long poiId) {
        poiRepo.findForUpdate(poiId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
        rejectServingRoutes(poiId);
        List<Route> references = routeStopRepo.findReferencingRoutes(poiId);
        if (!references.isEmpty()) throw new RouteConflictException("Hãy bỏ POI khỏi các Route trước khi xóa: " + routeNames(references));
        audioRepo.deleteAllInBatch(audioRepo.findByPoiId(poiId));
        translationRepo.deleteAllInBatch(translationRepo.findByPoiId(poiId));
        poiRepo.deleteById(poiId);
    }

    private void rejectServingRoutes(Long poiId) {
        List<Route> affected = routeStopRepo.findServingRoutes(poiId);
        if (!affected.isEmpty()) throw new RouteConflictException("Không thể xóa, ẩn/chuyển nháp hoặc làm mất tọa độ hợp lệ của POI đang phục vụ du khách. Route bị ảnh hưởng: " + routeNames(affected));
    }

    private String routeNames(List<Route> routes) {
        return routes.stream().map(route -> "'" + route.getName() + "' (#" + route.getId() + ")").collect(Collectors.joining(", "));
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
