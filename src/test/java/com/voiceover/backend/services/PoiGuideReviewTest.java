package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PoiGuideReviewTest {
    final PoiRepository pois = mock(PoiRepository.class);
    final TranslationRepository texts = mock(TranslationRepository.class);
    final AudioRepository audios = mock(AudioRepository.class);
    final LanguageRepository languages = mock(LanguageRepository.class);
    final TranslationReviewService pipeline = mock(TranslationReviewService.class);
    final AudioGenerationService files = mock(AudioGenerationService.class);
    final PoiService service = new PoiService(pois, texts, audios, languages, mock(RouteStopRepository.class), pipeline, mock(PoiThumbnailService.class), files);
    Poi poi;
    @BeforeEach void setup() {
        poi = Poi.builder().id(1L).name("POI kiểm thử").status("PUBLISHED").descriptionSource("Nguồn").build();
        when(pois.findById(1L)).thenReturn(Optional.of(poi));
        when(languages.findByIsDefaultTrue()).thenReturn(Optional.of(Language.builder().code("vi").isDefault(true).build()));
        when(files.hasUsableFile(anyString())).thenReturn(true);
    }
    void text(String lang) {
        when(texts.findByPoiIdAndLanguageCode(1L, lang)).thenReturn(Optional.of(Translation.builder().id(lang.equals("vi") ? 10L : 20L).content("Text " + lang).status("COMPLETED").build()));
    }
    void audio(String lang, String status) {
        text(lang);
        when(audios.findByPoiIdAndLanguageCode(1L, lang)).thenReturn(Optional.of(Audio.builder().translationId(lang.equals("vi") ? 10L : 20L).filePath("/audio/" + lang + ".wav").status(status).build()));
    }
    @SuppressWarnings("unchecked") List<Map<String, Object>> candidates(Map<String, Object> result) { return (List<Map<String, Object>>) result.get("audioCandidates"); }
    @Test void selectedReadyAudioWinsAndDefaultIsAvailableForBrowserFailure() {
        audio("en", "COMPLETED"); audio("vi", "COMPLETED");
        var result = service.getPoiAudioGuide(1L, "en");
        assertEquals("/audio/en.wav", result.get("audioUrl")); assertEquals("Text en", result.get("textContent"));
        assertEquals(List.of("en", "vi"), candidates(result).stream().map(c -> c.get("activeLanguage")).toList());
        assertFalse((Boolean) result.get("fallbackUsed")); verifyNoInteractions(pipeline);
    }
    @Test void missingSelectedAudioUsesConfiguredDefaultEvenWithSelectedText() {
        text("en"); audio("vi", "COMPLETED");
        var result = service.getPoiAudioGuide(1L, "en");
        assertEquals("vi", result.get("activeLanguage")); assertEquals("/audio/vi.wav", result.get("audioUrl"));
        assertTrue((Boolean) result.get("fallbackUsed"));
        assertEquals("Text en", ((Map<?, ?>)result.get("textFallback")).get("textContent")); verifyNoInteractions(pipeline);
    }
    @Test void failedProcessingPendingOrTextOnlyAudioNeverServed() {
        audio("vi", "COMPLETED");
        for (String status : List.of("FAILED", "PENDING", "PROCESSING", "TEXT_ONLY")) {
            audio("en", status); var result = service.getPoiAudioGuide(1L, "en");
            assertEquals("vi", result.get("activeLanguage")); assertEquals(1, candidates(result).size());
        }
    }
    @Test void missingOrBrokenFileUsesDefault() {
        audio("en", "COMPLETED"); audio("vi", "COMPLETED"); when(files.hasUsableFile("/audio/en.wav")).thenReturn(false);
        assertEquals("vi", service.getPoiAudioGuide(1L, "en").get("activeLanguage"));
    }
    @Test void bothMissingOrBrokenReturnRequestedTextWithoutBrowserSpeech() {
        audio("en", "COMPLETED"); audio("vi", "COMPLETED"); when(files.hasUsableFile(anyString())).thenReturn(false);
        var result = service.getPoiAudioGuide(1L, "en");
        assertEquals("Text en", result.get("textContent")); assertNull(result.get("audioUrl"));
        assertEquals("TEXT_ONLY", result.get("audioStatus")); assertFalse((Boolean)result.get("browserTtsAllowed")); assertTrue(candidates(result).isEmpty());
        verifyNoInteractions(pipeline);
    }
    @Test void missingTranslationUsesOriginalDescriptionAndSourceLanguage() {
        text("vi"); var result = service.getPoiAudioGuide(1L, "fr");
        assertEquals("Nguồn", result.get("textContent")); assertEquals("vi", result.get("activeLanguage")); assertNull(result.get("audioUrl"));
    }
    @Test void defaultLanguageIsConfiguredNotHardcoded() {
        when(languages.findByIsDefaultTrue()).thenReturn(Optional.of(Language.builder().code("fr").isDefault(true).build()));
        audio("fr", "COMPLETED"); var result = service.getPoiAudioGuide(1L, "en");
        assertEquals("fr", result.get("defaultLanguage")); assertEquals("/audio/fr.wav", result.get("audioUrl"));
        verify(audios, never()).findByPoiIdAndLanguageCode(1L, "vi");
    }
    @Test void selectedEqualsDefaultIsOnlyCheckedOnce() {
        audio("vi", "COMPLETED"); var result = service.getPoiAudioGuide(1L, "vi");
        assertEquals(1, candidates(result).size()); verify(audios, times(1)).findByPoiIdAndLanguageCode(1L, "vi");
    }
    @Test void audioForDifferentTranslationCannotBeExposed() {
        audio("en", "COMPLETED"); var wrong = audios.findByPoiIdAndLanguageCode(1L,"en").orElseThrow(); wrong.setTranslationId(999L);
        assertNull(service.getPoiAudioGuide(1L, "en").get("audioUrl"));
    }
    @Test void versionedPoiNeverServesStaleTranslationOrBuiltInText() {
        poi.setName("Đại học Sài Gòn"); poi.setContentRevision(2); poi.setSourceFingerprint("new-hash");
        poi.setSourceLanguageCode("fr"); poi.setDescriptionSource("Nouveau texte"); audio("vi", "COMPLETED");
        var result = service.getPoiAudioGuide(1L, "vi");
        assertEquals("Nouveau texte", result.get("textContent")); assertEquals("fr", result.get("activeLanguage")); assertNull(result.get("audioUrl"));
    }
    @Test void curatedManualTextStillOverridesBuiltInAndNeverQueuesPipeline() {
        poi.setName("Đại học Sài Gòn"); audio("vi", "COMPLETED");
        var text = texts.findByPoiIdAndLanguageCode(1L, "vi").orElseThrow(); text.setContent("Bản đã kiểm duyệt"); text.setManualEdit(true);
        assertEquals("Bản đã kiểm duyệt", service.getPoiAudioGuide(1L, "vi").get("textContent")); verifyNoInteractions(pipeline);
    }
    @Test void hiddenOrDraftPoiCannotBeListenedTo() {
        for (String status : List.of("HIDDEN", "DRAFT")) {
            poi.setStatus(status); assertThrows(IllegalArgumentException.class, () -> service.getPoiAudioGuide(1L, "vi"));
        }
    }
}
