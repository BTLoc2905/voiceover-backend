package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final LanguageRepository languageRepo;
    private final TranslationRepository translationRepo;
    private final AudioRepository audioRepo;
    private final ProtectedTermRepository protectedTermRepo;
    private final PipelineTaskRepository pipelineTaskRepo;
    private final SystemLogRepository systemLogRepo;
    private final PoiRepository poiRepo;
    private final TranslationReviewService reviewService;
    private final AudioGenerationService audioGenerator;

    @Value("${google.cloud.api.key:}")
    private String googleCloudApiKey;

    @Value("${app.translation.timeout-seconds:20}")
    private int translationTimeoutSeconds;

    @Value("${app.translation.cloud-url:https://translation.googleapis.com/language/translate/v2}")
    private String cloudTranslationUrl;

    @Value("${app.translation.unofficial-url:https://translate.googleapis.com/translate_a/single}")
    private String unofficialTranslationUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("pipelineExecutor")
    public void triggerFullPipelineForPoi(Poi poi) {
        if (poi.getDescriptionSource() == null || poi.getDescriptionSource().isBlank()) {
            log("pipeline", "ERROR", "Mô tả gốc của POI ID " + poi.getId() + " không hợp lệ (UC08-E3).");
            return;
        }

        String currentHash = sha256(poi.getDescriptionSource());
        List<Language> activeLanguages = languageRepo.findByIsActiveTrue();
        List<ProtectedTerm> terms = protectedTermRepo.findAll();

        for (Language lang : activeLanguages) {
            PipelineTask task = null;
            try {
                task = reviewService.reserveTranslation(poi.getId(), lang.getCode(), currentHash);
                if (task != null) processLanguageTranslationAndTts(poi, lang, terms, currentHash, task);
            } catch (Exception error) {
                if (task != null) {
                    try { failTask(task, "Pipeline thất bại: " + safeMessage(error)); }
                    catch (Exception persistenceError) {
                        log("pipeline", "ERROR", "Không lưu được lỗi pipeline POI " + poi.getId()
                                + " [" + lang.getCode() + "]: " + safeMessage(persistenceError));
                    }
                }
                log("pipeline", "ERROR", "Không xử lý được POI " + poi.getId() + " ["
                        + lang.getCode() + "]: " + safeMessage(error));
            }
        }
    }

    @Transactional
    public List<Long> requeueInterruptedTasks() {
        List<PipelineTask> interrupted = pipelineTaskRepo.findByTaskTypeAndStatusIn(
                "TRANSLATE_TTS", List.of("PROCESSING"));
        for (PipelineTask task : interrupted) {
            task.setStatus("PENDING");
            task.setErrorMessage("Đang khôi phục tác vụ bị gián đoạn khi backend khởi động lại.");
            pipelineTaskRepo.save(task);
        }
        return interrupted.stream().map(PipelineTask::getId).toList();
    }

    @Async("pipelineExecutor")
    public void resumeTask(Long taskId) {
        PipelineTask task = pipelineTaskRepo.findById(taskId).orElse(null);
        if (task == null || !"PENDING".equals(task.getStatus())
                || !"TRANSLATE_TTS".equals(task.getTaskType())) return;
        Poi poi = poiRepo.findById(task.getPoiId()).orElse(null);
        Language language = languageRepo.findById(task.getTargetLanguageCode()).orElse(null);
        if (poi == null || language == null) {
            failTask(task, "Không tìm thấy POI hoặc ngôn ngữ để khôi phục tác vụ.");
            return;
        }
        try {
            task.setStatus("PROCESSING");
            task.setErrorMessage(null);
            pipelineTaskRepo.save(task);
            processLanguageTranslationAndTts(poi, language, protectedTermRepo.findAll(),
                    sha256(poi.getDescriptionSource() == null ? "" : poi.getDescriptionSource()), task);
        } catch (Exception error) {
            failTask(task, "Khôi phục pipeline thất bại: " + safeMessage(error));
        }
    }

    public void failRecoveredTask(Long taskId, String message) {
        pipelineTaskRepo.findById(taskId).ifPresent(task -> failTask(task, message));
    }

    private void processLanguageTranslationAndTts(Poi poi, Language lang, List<ProtectedTerm> terms, String sourceHash, PipelineTask task) {
        if (poi.getSourceLanguageCode() == null || poi.getSourceLanguageCode().isBlank()) {
            failTask(task, "POI chưa có mã ngôn ngữ nguồn hợp lệ.");
            return;
        }
        Translation translation = translationRepo.findByPoiIdAndLanguageCode(poi.getId(), lang.getCode())
                .orElse(Translation.builder().poiId(poi.getId()).languageCode(lang.getCode()).build());
        if (translation.isManualEdit() && "COMPLETED".equals(translation.getStatus())
                && !sourceHash.equals(translation.getSourceHash())) {
            failTask(task, "Mô tả nguồn đã đổi; giữ nguyên bản dịch Admin chỉnh tay. Hãy rà soát và lưu lại bản dịch trước khi tạo audio.");
            return;
        }
        boolean translationReady = sourceHash.equals(translation.getSourceHash())
                && "COMPLETED".equals(translation.getStatus())
                && translation.getContent() != null && !translation.getContent().isBlank();
        if ("TTS_ONLY".equals(task.getTaskType()) && !translationReady) {
            failTask(task, "Bản dịch không còn khớp nội dung nguồn. Hãy lưu lại POI để chạy dịch và tạo audio.");
            return;
        }

        if (!translationReady) {
            translation.setStatus("PROCESSING");
            translationRepo.save(translation);
            String translatedText = translateWithRetry(poi.getDescriptionSource(), poi.getSourceLanguageCode(),
                    lang.getCode(), terms, task);
            if (translatedText == null) return;
            translation.setContent(translatedText);
            translation.setSourceHash(sourceHash);
            translation.setManualEdit(false);
            translation.setStatus("COMPLETED");
            translation = translationRepo.save(translation);
            task.setRetryCount(0);
        }

        // Bước 2: Kiểm tra ngôn ngữ có hỗ trợ TTS không (UC08-E4)
        if (!lang.isTtsSupported()) {
            Audio textOnlyAudio = audioRepo.findByPoiIdAndLanguageCode(poi.getId(), lang.getCode())
                    .orElse(Audio.builder().poiId(poi.getId()).languageCode(lang.getCode()).translationId(translation.getId()).build());
            textOnlyAudio.setStatus("TEXT_ONLY");
            textOnlyAudio.setFilePath(null);
            audioRepo.save(textOnlyAudio);

            task.setStatus("COMPLETED");
            task.setErrorMessage(null);
            task.setFinishedAt(LocalDateTime.now());
            pipelineTaskRepo.save(task);
            return;
        }

        // Không có key trả bản văn bản để frontend đọc bằng SpeechSynthesis miễn phí.
        if (audioGenerator.provider(lang.getCode()).equals("UNAVAILABLE")) {
            Audio textOnlyAudio = audioRepo.findByPoiIdAndLanguageCode(poi.getId(), lang.getCode())
                    .orElse(Audio.builder().poiId(poi.getId()).languageCode(lang.getCode())
                            .translationId(translation.getId()).build());
            textOnlyAudio.setStatus("TEXT_ONLY");
            textOnlyAudio.setFilePath(null);
            audioRepo.save(textOnlyAudio);
            task.setStatus("COMPLETED");
            task.setErrorMessage(null);
            task.setFinishedAt(LocalDateTime.now());
            pipelineTaskRepo.save(task);
            log("pipeline", "INFO", "Đã hoàn tất bản dịch; dùng giọng đọc trình duyệt cho POI "
                    + poi.getId() + " [" + lang.getCode() + "]");
            return;
        }

        // Bước 3: Tổng hợp giọng nói TTS ra file MP3 thật (Thử lại tối đa 3 lần - UC08-E2)
        generateAudioWithRetry(translation, task);
    }

    private void generateAudioWithRetry(Translation translation, PipelineTask task) {
        Audio audio = audioRepo.findByPoiIdAndLanguageCode(translation.getPoiId(), translation.getLanguageCode())
                .orElse(Audio.builder().poiId(translation.getPoiId()).languageCode(translation.getLanguageCode()).build());
        audio.setTranslationId(translation.getId()); audio.setStatus("PROCESSING"); audio.setFilePath(null); audio.setDurationSec(null);
        audioRepo.save(audio);
        int firstAttempt = Math.max(1, task.getRetryCount() + 1);
        if (firstAttempt > 3) {
            audio.setStatus("FAILED"); audioRepo.save(audio);
            failTask(task, "Đã hết số lần thử tạo audio. Hãy tạo lại audio để thử lại.");
            return;
        }
        for (int attempt = firstAttempt; attempt <= 3; attempt++) {
            try {
                var generated = audioGenerator.generate(translation.getPoiId(), translation.getLanguageCode(), translation.getContent());
                audio.setFilePath(generated.url()); audio.setDurationSec(generated.durationSec()); audio.setStatus("COMPLETED"); audioRepo.save(audio);
                task.setStatus("COMPLETED"); task.setRetryCount(attempt - 1); task.setErrorMessage(null);
                task.setFinishedAt(LocalDateTime.now()); pipelineTaskRepo.save(task);
                log("pipeline", "INFO", "Đã tạo audio đầy đủ cho POI " + translation.getPoiId() + " [" + translation.getLanguageCode() + "]");
                return;
            } catch (Exception error) {
                task.setRetryCount(attempt);
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                if (attempt >= 3 || !ProviderRetry.isRetryable(error)) {
                    audio.setStatus("FAILED"); audioRepo.save(audio);
                    failTask(task, "Tạo audio thất bại: " + safeMessage(error)); return;
                }
                try {
                    ProviderRetry.pause(attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    audio.setStatus("FAILED"); audioRepo.save(audio);
                    failTask(task, "Tạo audio bị gián đoạn. Có thể thử lại sau.");
                    return;
                }
            }
        }
    }

    private String translateWithRetry(String sourceText, String sourceLang, String targetLang,
            List<ProtectedTerm> terms, PipelineTask task) {
        for (int attempt = Math.max(1, task.getRetryCount() + 1); attempt <= 3; attempt++) {
            try {
                String translated = translateRealApiWithProtectedTerms(sourceText, sourceLang, targetLang, terms);
                if (translated == null || translated.isBlank()) throw new IOException("Dịch vụ không trả nội dung bản dịch.");
                return translated;
            } catch (Exception error) {
                task.setRetryCount(attempt);
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                if (attempt >= 3 || !ProviderRetry.isRetryable(error)) {
                    Translation translation = translationRepo.findByPoiIdAndLanguageCode(
                            task.getPoiId(), task.getTargetLanguageCode()).orElse(null);
                    if (translation != null) {
                        translation.setStatus("FAILED");
                        translationRepo.save(translation);
                    }
                    failTask(task, "Dịch thuật thất bại: " + safeMessage(error));
                    return null;
                }
                try {
                    ProviderRetry.pause(attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failTask(task, "Dịch thuật bị gián đoạn. Có thể thử lại sau.");
                    return null;
                }
            }
        }
        return null;
    }

    // Dịch theo source language của POI và giữ nguyên protected terms.
    private String translateRealApiWithProtectedTerms(String sourceText, String sourceLang,
            String targetLang, List<ProtectedTerm> terms) throws Exception {
        if (sourceLang.equalsIgnoreCase(targetLang)) {
            return sourceText;
        }

        // Thay thế các từ khóa cần bảo vệ bằng token __PT_0__, __PT_1__ trước khi gửi dịch
        String maskedText = sourceText;
        Map<String, String> tokenMap = new LinkedHashMap<>();
        int idx = 0;
        for (ProtectedTerm pt : terms) {
            if (pt.getTerm() != null && !pt.getTerm().isBlank() && maskedText.contains(pt.getTerm())) {
                String token = "PTERM" + idx + "X";
                tokenMap.put(token, pt.getTerm());
                maskedText = maskedText.replace(pt.getTerm(), token);
                idx++;
            }
        }

        if (!googleCloudApiKey.isBlank()) {
            String body = objectMapper.writeValueAsString(Map.of("q", maskedText,
                    "source", sourceLang, "target", targetLang, "format", "text"));
            HttpRequest cloudRequest = HttpRequest.newBuilder(URI.create(
                            cloudTranslationUrl + "?key=" + URLEncoder.encode(googleCloudApiKey, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(translationTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> cloudResponse = httpClient.send(
                    cloudRequest, HttpResponse.BodyHandlers.ofString());
            if (cloudResponse.statusCode() / 100 != 2) {
                throw new ProviderRequestException("Google Cloud Translation", cloudResponse.statusCode());
            }
            String translated = objectMapper.readTree(cloudResponse.body())
                    .path("data").path("translations").path(0).path("translatedText").asText();
            if (translated.isBlank()) throw new IOException("Google Cloud Translation không trả nội dung bản dịch.");
            for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
                translated = translated.replace(entry.getKey(), entry.getValue());
            }
            return translated;
        }

        String url = unofficialTranslationUrl + "?client=gtx&sl="
                + URLEncoder.encode(sourceLang, StandardCharsets.UTF_8)
                + "&tl=" + URLEncoder.encode(targetLang, StandardCharsets.UTF_8)
                + "&dt=t&q=" + URLEncoder.encode(maskedText, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(translationTimeoutSeconds))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new ProviderRequestException("Dịch vụ dịch", response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        StringBuilder sb = new StringBuilder();
        if (root.isArray() && !root.isEmpty() && root.get(0).isArray()) {
            for (JsonNode sentenceNode : root.get(0)) {
                if (sentenceNode.isArray() && !sentenceNode.isEmpty()) {
                    sb.append(sentenceNode.get(0).asText());
                }
            }
        }

        String translated = sb.toString();
        if (translated.isBlank()) throw new IOException("Dịch vụ dịch không trả nội dung bản dịch.");
        // Khôi phục lại chính xác các thuật ngữ bảo vệ (protected_terms)
        for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
            translated = translated.replace(entry.getKey(), entry.getValue());
        }
        return translated;
    }

    private void failTask(PipelineTask task, String errorMsg) {
        task.setStatus("FAILED");
        task.setErrorMessage(errorMsg);
        task.setFinishedAt(LocalDateTime.now());
        pipelineTaskRepo.save(task);
        log("pipeline", "ERROR", errorMsg);
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private void log(String component, String severity, String message) {
        systemLogRepo.save(SystemLog.builder()
                .component(component)
                .severity(severity)
                .message(message)
                .build());
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
