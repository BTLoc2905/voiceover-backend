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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PipelineService {

    private final LanguageRepository languageRepo;
    private final TranslationRepository translationRepo;
    private final AudioRepository audioRepo;
    private final ProtectedTermRepository protectedTermRepo;
    private final PipelineTaskRepository pipelineTaskRepo;
    private final SystemLogRepository systemLogRepo;

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
            Translation existing = translationRepo.findByPoiIdAndLanguageCode(poi.getId(), lang.getCode()).orElse(null);

            // UC08-A2: Bỏ qua nếu mô tả gốc không thay đổi và đã xử lý COMPLETED
            if (existing != null && currentHash.equals(existing.getSourceHash()) && "COMPLETED".equals(existing.getStatus())) {
                continue;
            }

            PipelineTask task = pipelineTaskRepo.save(PipelineTask.builder()
                    .poiId(poi.getId())
                    .targetLanguageCode(lang.getCode())
                    .taskType("TRANSLATE_TTS")
                    .status("PROCESSING")
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build());

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
        if (googleCloudApiKey.isBlank()) {
            if (tryGeneratePiperAudio(translation)) {
                task.setStatus("COMPLETED");
                task.setFinishedAt(LocalDateTime.now());
                pipelineTaskRepo.save(task);
                return;
            }
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
        generateRealTtsMp3WithRetry(translation, task);
    }

    @Async("pipelineExecutor")
    public void regenerateSingleLanguageAudio(Long poiId, String languageCode, String updatedContent) {
        Translation translation = translationRepo.findByPoiIdAndLanguageCode(poiId, languageCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản dịch (UC09-E1)"));

        if (updatedContent != null && !updatedContent.isBlank()) {
            translation.setContent(updatedContent);
            translation.setManualEdit(true);
            translation.setStatus("COMPLETED");
            translation = translationRepo.save(translation);
        }

        PipelineTask task = pipelineTaskRepo.save(PipelineTask.builder()
                .poiId(poiId)
                .targetLanguageCode(languageCode)
                .taskType("TTS_ONLY")
                .status("PROCESSING")
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build());

        generateRealTtsMp3WithRetry(translation, task);
    }

    private void generateRealTtsMp3WithRetry(Translation translation, PipelineTask task) {
        Audio audio = audioRepo.findByPoiIdAndLanguageCode(translation.getPoiId(), translation.getLanguageCode())
                .orElse(Audio.builder()
                        .poiId(translation.getPoiId())
                        .languageCode(translation.getLanguageCode())
                        .translationId(translation.getId())
                        .build());
        audio.setStatus("PROCESSING");
        audioRepo.save(audio);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Path audioDir = Paths.get(audioStorage);
                Files.createDirectories(audioDir);

                String fileName = "poi_" + translation.getPoiId() + "_" + translation.getLanguageCode() + ".mp3";
                Path filePath = audioDir.resolve(fileName);

                // Cắt ngắn tối đa 200 ký tự cho Google TTS stream hoặc gọi ElevenLabs nếu có API key
                String textToSpeak = translation.getContent();
                if (textToSpeak.length() > 200) {
                    textToSpeak = textToSpeak.substring(0, 197) + "...";
                }

                if (googleCloudApiKey.isBlank()) {
                    throw new IllegalStateException("Thiếu GOOGLE_CLOUD_API_KEY cho Google Cloud TTS.");
                }
                String requestBody = "{\"input\":{\"text\":" + objectMapper.writeValueAsString(textToSpeak)
                        + "},\"voice\":{\"languageCode\":\"" + translation.getLanguageCode()
                        + "\",\"ssmlGender\":\"NEUTRAL\"},\"audioConfig\":{\"audioEncoding\":\"MP3\"}}";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://texttospeech.googleapis.com/v1/text:synthesize?key=" + googleCloudApiKey))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();
                HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 != 2) {
                    throw new IOException("Google Cloud TTS trả về HTTP " + res.statusCode());
                }
                String encodedAudio = objectMapper.readTree(res.body()).path("audioContent").asText(null);
                if (encodedAudio == null || encodedAudio.isBlank()) {
                    throw new IOException("Google Cloud TTS không trả về audioContent.");
                }
                Files.write(filePath, Base64.getDecoder().decode(encodedAudio));

                int estimatedSeconds = Math.max(8, translation.getContent().split("\\s+").length / 2);
                audio.setFilePath("/audio/" + fileName);
                audio.setDurationSec(estimatedSeconds);
                audio.setStatus("COMPLETED");
                audioRepo.save(audio);

                task.setStatus("COMPLETED");
                task.setFinishedAt(LocalDateTime.now());
                pipelineTaskRepo.save(task);
                log("pipeline", "INFO", "Đã dịch và xuất file MP3 thành công cho POI " + translation.getPoiId() + " [" + translation.getLanguageCode() + "]");
                return;
            } catch (Exception e) {
                task.setRetryCount(attempt);
                if (attempt == 3) {
                    audio.setStatus("FAILED");
                    audioRepo.save(audio);
                    failTask(task, "Tạo audio TTS thất bại sau 3 lần thử: " + e.getMessage());
                }
            }
        }
    }

    private boolean tryGeneratePiperAudio(Translation translation) {
        if (piperExecutable.isBlank() || piperModel.isBlank()) {
            return false;
        }
        Audio audio = audioRepo.findByPoiIdAndLanguageCode(translation.getPoiId(), translation.getLanguageCode())
                .orElse(Audio.builder()
                        .poiId(translation.getPoiId())
                        .languageCode(translation.getLanguageCode())
                        .translationId(translation.getId())
                        .build());
        Path audioDir = Paths.get(audioStorage);
        Path outputPath = audioDir.resolve(
                "poi_" + translation.getPoiId() + "_" + translation.getLanguageCode() + ".wav");
        try {
            Files.createDirectories(audioDir);
            Process process = new ProcessBuilder(
                    piperExecutable,
                    "--model", piperModel,
                    "--output_file", outputPath.toString())
                    .redirectErrorStream(true)
                    .start();
            try (var writer = process.outputWriter(StandardCharsets.UTF_8)) {
                writer.write(translation.getContent());
                writer.write(System.lineSeparator());
            }
            if (!process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)
                    || process.exitValue() != 0
                    || !Files.exists(outputPath)
                    || Files.size(outputPath) == 0) {
                process.destroyForcibly();
                return false;
            }
            audio.setFilePath("/audio/" + outputPath.getFileName());
            audio.setDurationSec(Math.max(8, translation.getContent().split("\\s+").length / 2));
            audio.setStatus("COMPLETED");
            audioRepo.save(audio);
            log("pipeline", "INFO", "Đã tạo audio Piper local cho POI " + translation.getPoiId()
                    + " [" + translation.getLanguageCode() + "]");
            return true;
        } catch (IOException exception) {
            log("pipeline", "INFO", "Không dùng được Piper local, frontend sẽ dùng Web Speech API: "
                    + exception.getMessage());
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log("pipeline", "INFO", "Piper local bị gián đoạn, frontend sẽ dùng Web Speech API.");
            return false;
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