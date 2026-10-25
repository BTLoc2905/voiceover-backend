package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PipelineServiceTest {
    @Test
    void translatesFromPoiSourceLanguageAndCompletesTextOnlyForUnsupportedTts() throws Exception {
        var languages = mock(LanguageRepository.class);
        var translations = mock(TranslationRepository.class);
        var audios = mock(AudioRepository.class);
        var terms = mock(ProtectedTermRepository.class);
        var tasks = mock(PipelineTaskRepository.class);
        var logs = mock(SystemLogRepository.class);
        var review = mock(TranslationReviewService.class);
        var audio = mock(AudioGenerationService.class);
        var poiRepository = mock(PoiRepository.class);
        var requestedSourceLanguage = new AtomicReference<String>();
        var persistedTranslation = new AtomicReference<Translation>();
        var persistedAudio = new AtomicReference<Audio>();
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            requestedSourceLanguage.set(exchange.getRequestURI().getRawQuery());
            int attempt = requests.incrementAndGet();
            byte[] response = (attempt == 1 ? "temporary error" : "[[[\"Bonjour traduit\",\"\",\"fr\"]]]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(attempt == 1 ? 503 : 200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var language = Language.builder().code("en").isActive(true).ttsSupported(false).build();
            var poi = Poi.builder().id(5L).descriptionSource("Bonjour").sourceLanguageCode("fr").build();
            var task = PipelineTask.builder().id(8L).poiId(5L).targetLanguageCode("en")
                    .taskType("TRANSLATE_TTS").status("PROCESSING").build();
            var translation = Translation.builder().id(12L).poiId(5L).languageCode("en").build();
            when(languages.findByIsActiveTrue()).thenReturn(List.of(language));
            when(terms.findAll()).thenReturn(List.of());
            when(review.reserveTranslation(eq(5L), eq("en"), anyString())).thenReturn(task);
            when(translations.findByPoiIdAndLanguageCode(5L, "en")).thenReturn(Optional.empty());
            when(translations.save(any())).thenAnswer(invocation -> {
                Translation saved = invocation.getArgument(0);
                if (saved.getId() == null) saved.setId(translation.getId());
                persistedTranslation.set(saved);
                return saved;
            });
            when(audios.findByPoiIdAndLanguageCode(5L, "en")).thenReturn(Optional.empty());
            when(audios.save(any())).thenAnswer(invocation -> {
                Audio saved = invocation.getArgument(0);
                persistedAudio.set(saved);
                return saved;
            });
            when(audio.provider("en")).thenReturn("UNAVAILABLE");
            when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            var service = new PipelineService(languages, translations, audios, terms, tasks, logs,
                    poiRepository, review, audio);
            ReflectionTestUtils.setField(service, "googleCloudApiKey", "");
            ReflectionTestUtils.setField(service, "unofficialTranslationUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/translate");
            ReflectionTestUtils.setField(service, "translationTimeoutSeconds", 2);

            service.triggerFullPipelineForPoi(poi);

            assertTrue(requestedSourceLanguage.get().contains("sl=fr"));
            assertEquals(2, requests.get(), "A transient provider failure should be retried once before success.");
            assertEquals("Bonjour traduit", persistedTranslation.get().getContent());
            assertEquals("COMPLETED", persistedTranslation.get().getStatus());
            assertEquals("TEXT_ONLY", persistedAudio.get().getStatus());
            assertEquals("COMPLETED", task.getStatus());
            verify(audio, never()).generate(any(), any(), any());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void translationProviderRetriesTransientFailuresButNotClientErrors() {
        assertTrue(ProviderRetry.isRetryable(new java.io.IOException("connection reset")));
        assertTrue(new ProviderRequestException("translation", 429).isRetryable());
        assertTrue(new ProviderRequestException("translation", 503).isRetryable());
        assertFalse(new ProviderRequestException("translation", 400).isRetryable());
        assertFalse(new ProviderRequestException("translation", 403).isRetryable());
        assertFalse(ProviderRetry.isRetryable(new IllegalStateException("provider not configured")));
    }

    @Test
    void restartRecoveryResumesAnInterruptedTranslationTaskWithoutRepeatingCompletedTranslation() throws Exception {
        var languages = mock(LanguageRepository.class);
        var translations = mock(TranslationRepository.class);
        var audios = mock(AudioRepository.class);
        var terms = mock(ProtectedTermRepository.class);
        var tasks = mock(PipelineTaskRepository.class);
        var logs = mock(SystemLogRepository.class);
        var review = mock(TranslationReviewService.class);
        var audio = mock(AudioGenerationService.class);
        var poiRepository = mock(PoiRepository.class);
        var source = "Bonjour";
        var sourceHash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8)));
        var poi = Poi.builder().id(5L).descriptionSource(source).sourceLanguageCode("fr").build();
        var language = Language.builder().code("en").isActive(true).ttsSupported(false).build();
        var task = PipelineTask.builder().id(8L).poiId(5L).targetLanguageCode("en")
                .taskType("TRANSLATE_TTS").status("PROCESSING").retryCount(1).build();
        var translation = Translation.builder().id(12L).poiId(5L).languageCode("en")
                .content("Already translated").sourceHash(sourceHash).status("COMPLETED").build();
        var persistedAudio = new AtomicReference<Audio>();
        when(tasks.findByTaskTypeAndStatusIn("TRANSLATE_TTS", List.of("PROCESSING"))).thenReturn(List.of(task));
        when(tasks.findById(8L)).thenReturn(Optional.of(task));
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(poiRepository.findById(5L)).thenReturn(Optional.of(poi));
        when(languages.findById("en")).thenReturn(Optional.of(language));
        when(translations.findByPoiIdAndLanguageCode(5L, "en")).thenReturn(Optional.of(translation));
        when(terms.findAll()).thenReturn(List.of());
        when(audios.findByPoiIdAndLanguageCode(5L, "en")).thenReturn(Optional.empty());
        when(audios.save(any())).thenAnswer(invocation -> {
            Audio saved = invocation.getArgument(0);
            persistedAudio.set(saved);
            return saved;
        });

        var service = new PipelineService(languages, translations, audios, terms, tasks, logs,
                poiRepository, review, audio);
        assertEquals(List.of(8L), service.requeueInterruptedTasks());
        assertEquals("PENDING", task.getStatus());
        service.resumeTask(8L);

        assertEquals("COMPLETED", task.getStatus());
        assertEquals("TEXT_ONLY", persistedAudio.get().getStatus());
        verify(translations, never()).save(any());
        verifyNoInteractions(audio);
    }
}
