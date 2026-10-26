package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PoiRouteProtectionTest {
    final PoiRepository pois = mock(PoiRepository.class);
    final RouteStopRepository stops = mock(RouteStopRepository.class);
    final TranslationRepository translations = mock(TranslationRepository.class);
    final AudioRepository audios = mock(AudioRepository.class);
    final TranslationReviewService pipeline = mock(TranslationReviewService.class);
    final LanguageRepository languages = mock(LanguageRepository.class);
    final PoiService service = new PoiService(pois, translations, audios,
            languages, stops, pipeline, mock(PoiThumbnailService.class));
    Poi poi(String status) {
        Poi poi = Poi.builder().id(1L).name("POI").descriptionSource("Mô tả").sourceLanguageCode("vi").status(status).latitude(BigDecimal.TEN).longitude(BigDecimal.TEN).build();
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        when(pois.findForUpdate(1L)).thenReturn(Optional.of(poi)); return poi;
    }
    @Test void servedPoiCannotBeDeletedHiddenDraftOrLoseCoordinates() {
        Route route = Route.builder().id(7L).name("Route đang phục vụ").build();
        when(stops.findServingRoutes(1L)).thenReturn(List.of(route));
        poi("PUBLISHED");
        assertTrue(assertThrows(RouteConflictException.class, () -> service.deletePoiSafely(1L)).getMessage().contains("Route đang phục vụ"));
        for (String status : List.of("DRAFT", "HIDDEN")) assertThrows(RouteConflictException.class, () -> service.saveOrUpdatePoi(poi(status)));
        Poi invalid = poi("PUBLISHED"); invalid.setLatitude(null);
        assertThrows(RouteConflictException.class, () -> service.saveOrUpdatePoi(invalid));
        verify(pois, never()).save(any()); verify(pois, never()).deleteById(any()); verifyNoInteractions(pipeline);
    }
    @Test void closedSessionAllowsPoiDraftButReferencedPoiDeletionHasClearConflict() {
        when(stops.findServingRoutes(1L)).thenReturn(List.of());
        Poi draft = poi("DRAFT"); when(pois.saveAndFlush(draft)).thenReturn(draft);
        assertEquals("DRAFT", service.saveOrUpdatePoi(draft).getStatus());
        when(stops.findReferencingRoutes(1L)).thenReturn(List.of(Route.builder().id(9L).name("Bản nháp còn tham chiếu").build()));
        assertTrue(assertThrows(RouteConflictException.class, () -> service.deletePoiSafely(1L)).getMessage().contains("Bản nháp còn tham chiếu"));
        verify(pois, never()).deleteById(any());
    }
    @Test void publicGuideRejectsDraftAndHiddenBeforeLoadingOrGeneratingNarration() {
        for (String status : List.of("DRAFT", "HIDDEN")) {
            Poi unavailable = poi(status);
            when(pois.findById(1L)).thenReturn(Optional.of(unavailable));
            assertThrows(IllegalArgumentException.class, () -> service.getPoiAudioGuide(1L, "vi"));
        }
        verifyNoInteractions(pipeline);
    }

    @Test void unreferencedPoiDeletesAudioThenTranslationThenPoi() {
        poi("DRAFT");
        Audio audio = Audio.builder().id(10L).poiId(1L).translationId(20L).languageCode("vi").build();
        Translation translation = Translation.builder().id(20L).poiId(1L).languageCode("vi").build();
        when(audios.findByPoiId(1L)).thenReturn(List.of(audio));
        when(translations.findByPoiId(1L)).thenReturn(List.of(translation));

        service.deletePoiSafely(1L);

        var order = inOrder(audios, translations, pois);
        order.verify(audios).deleteAllInBatch(List.of(audio));
        order.verify(translations).deleteAllInBatch(List.of(translation));
        order.verify(pois).deleteById(1L);
    }

    @Test void publishedPoiWithoutRouteCanBeDeletedAfterConfirmation() {
        poi("PUBLISHED");
        when(stops.findServingRoutes(1L)).thenReturn(List.of());
        when(stops.findReferencingRoutes(1L)).thenReturn(List.of());

        service.deletePoiSafely(1L);

        verify(pois).deleteById(1L);
    }
}
