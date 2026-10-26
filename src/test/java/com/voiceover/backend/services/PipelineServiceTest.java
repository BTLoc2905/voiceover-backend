package com.voiceover.backend.services;

import com.sun.net.httpserver.HttpServer;
import com.voiceover.backend.entities.ProtectedTerm;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PipelineServiceTest {
    final ContentPipelineFixture f = new ContentPipelineFixture();
    final PipelineService worker = new PipelineService(f.service, f.generator);

    void translationUrl(String url) {
        ReflectionTestUtils.setField(worker, "googleCloudApiKey", "");
        ReflectionTestUtils.setField(worker, "unofficialTranslationUrl", url);
        ReflectionTestUtils.setField(worker, "translationTimeoutSeconds", 2);
    }

    @Test void translatesFromSnapshotSourceWithProtectedTermAndRetries503() throws Exception {
        f.en.setTtsSupported(false);
        when(f.terms.findAll()).thenReturn(List.of(ProtectedTerm.builder().term("Saigon").build()));
        f.enqueue(); AtomicInteger requests = new AtomicInteger(); AtomicReference<String> query = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            query.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            int attempt = requests.incrementAndGet();
            byte[] response = (attempt == 1 ? "temporary" : "[[[\"Hello PTERM0X\",\"\",\"fr\"]]]").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(attempt == 1 ? 503 : 200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            translationUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/translate");
            worker.resumeTask(f.latest("en").getId()).join();
            assertEquals(2, requests.get()); assertTrue(query.get().contains("sl=fr")); assertTrue(query.get().contains("q=Bonjour PTERM0X"));
            assertEquals("Hello Saigon", f.translations.get("en").getContent());
            assertEquals("TEXT_ONLY", f.files.get("en").getStatus()); assertEquals("COMPLETED", f.latest("en").getStatus());
            assertEquals(2, f.latest("en").getTranslationAttempts()); verify(f.generator, never()).generate(any(), any(), any());
        } finally { server.stop(0); }
    }

    @Test void ttsRetriesTransientFailuresAndStoresOnlyTheSuccessfulFile() throws Exception {
        f.poi.setSourceLanguageCode("en"); f.enqueue();
        var result = new AudioGenerationService.GeneratedAudio("/storage/audio/new.wav", 5);
        when(f.generator.generate(1L, "en", "Bonjour Saigon")).thenThrow(new IOException("temporary"))
                .thenThrow(new IOException("temporary")).thenReturn(result);
        worker.resumeTask(f.latest("en").getId()).join();
        verify(f.generator, times(3)).generate(1L, "en", "Bonjour Saigon");
        assertEquals("COMPLETED", f.latest("en").getStatus()); assertEquals(3, f.latest("en").getAudioAttempts());
        assertEquals(2, f.latest("en").getRetryCount()); assertEquals(result.url(), f.files.get("en").getFilePath());
    }

    @Test void missingProviderFailsWithoutCallingItThreeTimesOrDiscardingTheTranslation() throws Exception {
        f.poi.setSourceLanguageCode("en"); f.enqueue();
        when(f.generator.generate(any(), any(), any())).thenThrow(new IllegalStateException("Chưa cấu hình TTS"));
        worker.resumeTask(f.latest("en").getId()).join();
        verify(f.generator, times(1)).generate(any(), any(), any());
        assertEquals("FAILED", f.latest("en").getStatus()); assertEquals("FAILED", f.files.get("en").getStatus());
        assertNull(f.files.get("en").getFilePath()); assertEquals("COMPLETED", f.translations.get("en").getStatus());
        assertTrue(f.latest("en").getErrorMessage().contains("Chưa cấu hình TTS"));
    }

    @Test void restartBetweenTranslationAndAudioDoesNotCallTranslationAgain() throws Exception {
        f.enqueue(); f.translate("en", "Persisted English");
        f.latest("en").setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        translationUrl("http://127.0.0.1:1/must-not-be-called");
        when(f.generator.generate(1L, "en", "Persisted English")).thenReturn(new AudioGenerationService.GeneratedAudio("/new.wav", 5));
        worker.resumeTask(f.latest("en").getId()).join();
        assertEquals("COMPLETED", f.latest("en").getStatus()); assertEquals(0, f.latest("en").getTranslationAttempts());
        assertEquals(1, f.latest("en").getAudioAttempts());
    }

    @Test void lateGeneratedFileIsDiscardedAndNeverPublishedAfterSourceUpdate() throws Exception {
        f.poi.setSourceLanguageCode("en"); f.enqueue();
        var result = new AudioGenerationService.GeneratedAudio("/storage/audio/rejected.wav", 5);
        when(f.generator.generate(any(), any(), any())).thenAnswer(call -> {
            f.poi.setDescriptionSource("New source B"); f.enqueue(); return result;
        });
        worker.resumeTask(f.latest("en").getId()).join();
        verify(f.generator).discard(result); assertNull(f.files.get("en").getFilePath());
        assertEquals("PENDING", f.latest("en").getStatus()); assertEquals(2, f.latest("en").getSourceRevision());
    }

    @Test void exhaustedRecoveredTranslationTaskFailsInsteadOfStayingProcessingForever() {
        f.enqueue(); f.claim("en"); f.latest("en").setTranslationAttempts(3);
        f.latest("en").setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        translationUrl("http://127.0.0.1:1/must-not-be-called");
        worker.resumeTask(f.latest("en").getId()).join();
        assertEquals("FAILED", f.latest("en").getStatus()); assertEquals(3, f.latest("en").getTranslationAttempts());
        assertTrue(f.latest("en").getErrorMessage().contains("3 lần"));
    }

    @Test void languageFailureDoesNotStopOtherLanguageJobs() throws Exception {
        f.poi.setSourceLanguageCode("en"); f.enqueue();
        translationUrl("http://127.0.0.1:1/unavailable");
        // Same-language source skips the translation API, even while the other language fails.
        when(f.generator.generate(1L, "en", "Bonjour Saigon")).thenReturn(new AudioGenerationService.GeneratedAudio("/en.wav", 5));
        worker.resumeTask(f.latest("vi").getId()).join(); worker.resumeTask(f.latest("en").getId()).join();
        assertEquals("FAILED", f.latest("vi").getStatus()); assertEquals("COMPLETED", f.latest("en").getStatus());
    }

    @Test void retriesOnlyTransientErrors() {
        assertTrue(ProviderRetry.isRetryable(new IOException("connection reset")));
        assertTrue(new ProviderRequestException("translation", 429).isRetryable());
        assertTrue(new ProviderRequestException("translation", 503).isRetryable());
        assertFalse(new ProviderRequestException("translation", 400).isRetryable());
        assertFalse(new ProviderRequestException("translation", 403).isRetryable());
        assertFalse(ProviderRetry.isRetryable(new IllegalStateException("unconfigured")));
    }
}