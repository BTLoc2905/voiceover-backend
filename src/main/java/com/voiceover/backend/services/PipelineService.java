package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    private final TranslationReviewService reviewService;
    private final AudioGenerationService audioGenerator;

    @Value("${google.cloud.api.key:}")
    private String googleCloudApiKey;

    @Value("${app.piper.executable:}")
    private String piperExecutable;

    @Value("${app.piper.model:}")
    private String piperModel;

    @Value("${app.audio.storage:storage/audio}")
    private String audioStorage;

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
            PipelineTask task = reviewService.reserveTranslation(poi.getId(), lang.getCode(), currentHash);
            if (task == null) continue;
            processLanguageTranslationAndTts(poi, lang, terms, currentHash, task);
        }
    }

    private void processLanguageTranslationAndTts(Poi poi, Language lang, List<ProtectedTerm> terms, String sourceHash, PipelineTask task) {
        Translation translation = translationRepo.findByPoiIdAndLanguageCode(poi.getId(), lang.getCode())
                .orElse(Translation.builder().poiId(poi.getId()).languageCode(lang.getCode()).build());
        translation.setStatus("PROCESSING");
        translationRepo.save(translation);

        // Bước 1: Dịch thuật thực tế có bảo vệ thuật ngữ (Thử lại tối đa 3 lần - UC08-E1)
        String translatedText = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                translatedText = translateRealApiWithProtectedTerms(poi.getDescriptionSource(), lang.getCode(), terms);
                break;
            } catch (Exception e) {
                task.setRetryCount(attempt);
                if (attempt == 3) {
                    translation.setStatus("FAILED");
                    translationRepo.save(translation);
                    failTask(task, "Dịch thuật thất bại sau 3 lần thử: " + e.getMessage());
                    return;
                }
            }
        }

        translation.setContent(translatedText);
        translation.setSourceHash(sourceHash);
        translation.setManualEdit(false);
        translation.setStatus("COMPLETED");
        translation = translationRepo.save(translation);

        // Bước 2: Kiểm tra ngôn ngữ có hỗ trợ TTS không (UC08-E4)
        if (!lang.isTtsSupported()) {
            Audio textOnlyAudio = audioRepo.findByPoiIdAndLanguageCode(poi.getId(), lang.getCode())
                    .orElse(Audio.builder().poiId(poi.getId()).languageCode(lang.getCode()).translationId(translation.getId()).build());
            textOnlyAudio.setStatus("TEXT_ONLY");
            textOnlyAudio.setFilePath(null);
            audioRepo.save(textOnlyAudio);

            task.setStatus("COMPLETED");
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
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var generated = audioGenerator.generate(translation.getPoiId(), translation.getLanguageCode(), translation.getContent());
                audio.setFilePath(generated.url()); audio.setDurationSec(generated.durationSec()); audio.setStatus("COMPLETED"); audioRepo.save(audio);
                task.setStatus("COMPLETED"); task.setRetryCount(attempt - 1); task.setFinishedAt(LocalDateTime.now()); pipelineTaskRepo.save(task);
                log("pipeline", "INFO", "Đã tạo audio đầy đủ cho POI " + translation.getPoiId() + " [" + translation.getLanguageCode() + "]");
                return;
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                task.setRetryCount(attempt);
                if (attempt == 3 || error instanceof InterruptedException) {
                    audio.setStatus("FAILED"); audioRepo.save(audio);
                    failTask(task, "Tạo audio thất bại: " + error.getMessage()); return;
                }
            }
        }
    }

    // Dịch thuật thực tế qua Google Translate API + Giữ nguyên các từ khóa trong bảng protected_terms
    private String translateRealApiWithProtectedTerms(String sourceText, String targetLang, List<ProtectedTerm> terms) throws Exception {
        if ("vi".equalsIgnoreCase(targetLang)) {
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
            String body = "{\"q\":" + objectMapper.writeValueAsString(maskedText)
                    + ",\"source\":\"vi\",\"target\":\"" + targetLang + "\",\"format\":\"text\"}";
            HttpRequest cloudRequest = HttpRequest.newBuilder(URI.create(
                            "https://translation.googleapis.com/language/translate/v2?key=" + googleCloudApiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> cloudResponse = httpClient.send(
                    cloudRequest, HttpResponse.BodyHandlers.ofString());
            if (cloudResponse.statusCode() / 100 != 2) {
                throw new IOException("Google Cloud Translation trả về HTTP " + cloudResponse.statusCode());
            }
            String translated = objectMapper.readTree(cloudResponse.body())
                    .path("data").path("translations").path(0).path("translatedText").asText();
            for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
                translated = translated.replace(entry.getKey(), entry.getValue());
            }
            return translated;
        }

        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=vi&tl="
                + URLEncoder.encode(targetLang, StandardCharsets.UTF_8)
                + "&dt=t&q=" + URLEncoder.encode(maskedText, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP Error " + response.statusCode());
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
