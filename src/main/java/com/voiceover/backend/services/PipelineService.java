package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineService {
    private final TranslationReviewService jobs;
    private final AudioGenerationService audioGenerator;
    private final Map<Long, TranslationReviewService.Work> active = new ConcurrentHashMap<>();

    @Value("${google.cloud.api.key:}") private String googleCloudApiKey;
    @Value("${app.translation.timeout-seconds:20}") private int translationTimeoutSeconds;
    @Value("${app.translation.cloud-url:https://translation.googleapis.com/language/translate/v2}") private String cloudTranslationUrl;
    @Value("${app.translation.unofficial-url:https://translate.googleapis.com/translate_a/single}") private String unofficialTranslationUrl;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Both source-pipeline and review jobs use the same claim and publication checks. */
    @Async("pipelineExecutor")
    public CompletableFuture<Void> resumeTask(Long taskId) {
        TranslationReviewService.Work work = null;
        boolean translating = false;
        try {
            work = jobs.claim(taskId);
            if (work == null) return CompletableFuture.completedFuture(null);
            active.put(taskId, work);
            if (work.text() == null) {
                translating = true;
                final var input = work;
                String translated = callWithRetry(input, true, () -> translateRealApiWithProtectedTerms(
                        input.sourceText(), input.sourceLanguage(), input.code(), input.terms()));
                if (translated == null) return CompletableFuture.completedFuture(null);
                work = jobs.saveTranslation(work, translated);
                if (work == null) return CompletableFuture.completedFuture(null);
            }
            translating = false;
            if (!work.ttsSupported()) {
                jobs.complete(work, null);
                return CompletableFuture.completedFuture(null);
            }
            final var input = work;
            var generated = callWithRetry(input, false, () -> audioGenerator.generate(input.poiId(), input.code(), input.text()));
            if (generated != null && !jobs.complete(work, generated)) audioGenerator.discard(generated);
        } catch (Exception error) {
            if (work != null) {
                try { jobs.fail(work, translating, "Pipeline thất bại: " + safeMessage(error)); }
                catch (Exception persistenceError) { log.error("Không lưu được trạng thái task {}; lease sẽ được nhận lại", taskId, persistenceError); }
            } else log.error("Không nhận được task {}; giữ trong DB để thử lại", taskId, error);
        } finally {
            active.remove(taskId);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Scheduled(fixedDelay = 10000)
    public void renewLeases() {
        for (var work : active.values()) {
            try { jobs.renewLease(work); }
            catch (Exception error) { log.warn("Không gia hạn được task {}", work.taskId(), error); }
        }
    }

    private <T> T callWithRetry(TranslationReviewService.Work work, boolean translating, Callable<T> provider) throws Exception {
        while (true) {
            int attempt = jobs.nextAttempt(work, translating);
            if (attempt == 0) return null;
            try { return provider.call(); }
            catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                if (attempt >= 3 || !ProviderRetry.isRetryable(error)) {
                    jobs.fail(work, translating, (translating ? "Dịch thuật thất bại: " : "Tạo audio thất bại: ") + safeMessage(error));
                    return null;
                }
                ProviderRetry.pause(attempt);
            }
        }
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? error.getClass().getSimpleName() : error.getMessage();
    }

    // Dịch theo source language của POI và giữ nguyên protected terms.
    private String translateRealApiWithProtectedTerms(String sourceText, String sourceLang,
            String targetLang, List<String> terms) throws Exception {
        if (sourceLang.equalsIgnoreCase(targetLang)) {
            return sourceText;
        }

        // Thay thế các từ khóa cần bảo vệ bằng token __PT_0__, __PT_1__ trước khi gửi dịch
        String maskedText = sourceText;
        Map<String, String> tokenMap = new LinkedHashMap<>();
        int idx = 0;
        for (String term : terms) {
            if (term != null && !term.isBlank() && maskedText.contains(term)) {
                String token = "PTERM" + idx + "X";
                tokenMap.put(token, term);
                maskedText = maskedText.replace(term, token);
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

}
