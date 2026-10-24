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
    private final PipelineService pipelineService;
    private final OpenStreetMapPoiService openStreetMapPoiService;
    private final WikimediaImageService wikimediaImageService;
    private final TranslationReviewService reviewService;
    private final PoiThumbnailService thumbnailService;

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

        String activeLang = requestedLang;
        Optional<Translation> transOpt = completedTranslation(poiId, requestedLang);
        String builtInNarration = transOpt.isPresent() ? null : availableBuiltIn(poi, requestedLang);
        if (transOpt.isEmpty() && builtInNarration == null) {
            activeLang = defaultLang;
            transOpt = completedTranslation(poiId, defaultLang);
            builtInNarration = transOpt.isPresent() ? null : availableBuiltIn(poi, defaultLang);
        }
        boolean fallbackUsed = !Objects.equals(activeLang, requestedLang);
        String textContent = transOpt.map(Translation::getContent).orElse(builtInNarration == null ? poi.getDescriptionSource() : builtInNarration);
        Optional<Audio> audioOpt = transOpt.isPresent() ? audioRepo.findByPoiIdAndLanguageCode(poiId, activeLang) : Optional.empty();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("poiId", poi.getId());
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

    private Optional<Translation> completedTranslation(Long poiId, String language) {
        return translationRepo.findByPoiIdAndLanguageCode(poiId, language)
                .filter(text -> "COMPLETED".equals(text.getStatus()) && text.getContent() != null && !text.getContent().isBlank());
    }
    private String availableBuiltIn(Poi poi, String language) {
        return Set.of("Đại học Sài Gòn", "Công viên 23 tháng 9", "Chợ An Đông", "Nhà thờ Chợ Quán").contains(poi.getName())
                ? OpenStreetMapPoiService.localizedNarrationFor(poi.getName(), language) : null;
    }

    // UC06 & UC08: Tạo mới hoặc cập nhật POI và kích hoạt Pipeline tự động
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Poi saveOrUpdatePoi(Poi poiRequest) {
        if (poiRequest == null) throw new IllegalArgumentException("Dữ liệu POI không hợp lệ.");
        if (poiRequest.getId() != null) {
            Poi previous = poiRepo.findForUpdate(poiRequest.getId()).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
            if (!Objects.equals(previous.getDescriptionSource(), poiRequest.getDescriptionSource())
                    || !Objects.equals(previous.getSourceLanguageCode(), poiRequest.getSourceLanguageCode())) reviewService.assertSourceEditable(poiRequest.getId());
            if (!RouteSessionService.published(poiRequest.getStatus()) || !RouteSessionService.hasValidCoordinates(poiRequest)) rejectServingRoutes(poiRequest.getId());
        }
        validatePoi(poiRequest);
        poiRequest.setName(poiRequest.getName().strip());
        poiRequest.setDescriptionSource(poiRequest.getDescriptionSource().strip());
        poiRequest.setStatus(poiRequest.getStatus().toUpperCase(Locale.ROOT));
        Poi saved = poiRepo.save(poiRequest);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { pipelineService.triggerFullPipelineForPoi(saved); }
            });
        } else pipelineService.triggerFullPipelineForPoi(saved);
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
