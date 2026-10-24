package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PoiGuideReviewTest {
    final PoiRepository pois=mock(PoiRepository.class);
    final TranslationRepository texts=mock(TranslationRepository.class);
    final AudioRepository audios=mock(AudioRepository.class);
    final LanguageRepository languages=mock(LanguageRepository.class);
    final PipelineService pipeline=mock(PipelineService.class);
    final PoiService service=new PoiService(pois,texts,audios,languages,mock(RouteStopRepository.class),pipeline,mock(OpenStreetMapPoiService.class),mock(WikimediaImageService.class),mock(TranslationReviewService.class));
    void setup(String name) {
        when(pois.findById(1L)).thenReturn(Optional.of(Poi.builder().id(1L).name(name).status("PUBLISHED").descriptionSource("Nguồn").build()));
        var language=Language.builder().code("vi").isActive(true).isDefault(true).ttsSupported(true).build();
        when(languages.findByIsDefaultTrue()).thenReturn(Optional.of(language)); when(languages.findById("vi")).thenReturn(Optional.of(language));
    }
    @Test void curatedDatabaseTextAndNewAudioOverrideTheBuiltInNarration() {
        setup("Đại học Sài Gòn");
        when(texts.findByPoiIdAndLanguageCode(1L,"vi")).thenReturn(Optional.of(Translation.builder().content("Bản đã kiểm duyệt").isManualEdit(true).status("COMPLETED").build()));
        when(audios.findByPoiIdAndLanguageCode(1L,"vi")).thenReturn(Optional.of(Audio.builder().filePath("/storage/audio/new.wav").status("COMPLETED").build()));
        var guide=service.getPoiAudioGuide(1L,"vi"); assertEquals("Bản đã kiểm duyệt",guide.get("textContent")); assertEquals("/storage/audio/new.wav",guide.get("audioUrl")); verifyNoInteractions(pipeline);
    }
    @Test void missingTranslationKeepsExistingBuiltInBrowserNarrationWithoutStartingAPipeline() {
        setup("Đại học Sài Gòn"); var guide=service.getPoiAudioGuide(1L,"vi");
        assertFalse(((String)guide.get("textContent")).isBlank()); assertTrue((Boolean)guide.get("browserTtsAllowed")); assertNull(guide.get("audioUrl")); verifyNoInteractions(pipeline);
    }
    @Test void fallbackUsesDefaultTextAndDoesNotExposeAnOldFileWhileRegenerating() {
        setup("POI kiểm thử");
        when(texts.findByPoiIdAndLanguageCode(1L,"vi")).thenReturn(Optional.of(Translation.builder().content("Bản mới").status("COMPLETED").build()));
        when(audios.findByPoiIdAndLanguageCode(1L,"vi")).thenReturn(Optional.of(Audio.builder().filePath("/audio/stale.mp3").status("PROCESSING").build()));
        var guide=service.getPoiAudioGuide(1L,"fr"); assertEquals("vi",guide.get("activeLanguage")); assertTrue((Boolean)guide.get("fallbackUsed")); assertNull(guide.get("audioUrl")); verifyNoInteractions(pipeline);
    }
}
