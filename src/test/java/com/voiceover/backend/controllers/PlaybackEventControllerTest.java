package com.voiceover.backend.controllers;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaybackEventControllerTest {
    final PoiRepository pois = mock(PoiRepository.class);
    final LanguageRepository languages = mock(LanguageRepository.class);
    final UsageEventRepository events = mock(UsageEventRepository.class);
    final PlaybackEventController controller = new PlaybackEventController(pois, languages, events);
    @Test void recordsActualStartOncePerPlaybackAndCompletionSeparately() {
        when(pois.findById(1L)).thenReturn(Optional.of(Poi.builder().id(1L).status("PUBLISHED").build()));
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        var start = new PlaybackEventController.Input(1L, "vi", "AUTO_PLAY", "client", "playback-1");
        assertTrue(controller.record(start).get("recorded"));
        assertFalse(controller.record(start).get("recorded"));
        assertTrue(controller.record(new PlaybackEventController.Input(1L, "vi", "AUDIO_COMPLETED", "client", "playback-1")).get("recorded"));
        verify(events, times(2)).saveAndFlush(any());
    }
    @Test void rejectsPreloadUnknownPoiAndInactiveLanguageWithoutRecording() {
        assertThrows(IllegalArgumentException.class, () -> controller.record(new PlaybackEventController.Input(1L, "vi", "PRELOAD", "client", "p")));
        assertThrows(IllegalArgumentException.class, () -> controller.record(new PlaybackEventController.Input(1L, "vi", "AUTO_PLAY", "client", "p")));
        when(pois.findById(1L)).thenReturn(Optional.of(Poi.builder().id(1L).status("PUBLISHED").build()));
        assertThrows(IllegalArgumentException.class, () -> controller.record(new PlaybackEventController.Input(1L, "vi", "AUTO_PLAY", "client", "p")));
        verifyNoInteractions(events);
    }
    @Test void retryAfterFailedWriteCanStillRecord() {
        when(pois.findById(1L)).thenReturn(Optional.of(Poi.builder().id(1L).status("PUBLISHED").build()));
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        when(events.saveAndFlush(any())).thenThrow(new IllegalStateException("DB failed")).thenReturn(new UsageEvent());
        var input = new PlaybackEventController.Input(1L, "vi", "TAP_POI", "client", "p");
        assertThrows(IllegalStateException.class, () -> controller.record(input));
        assertTrue(controller.record(input).get("recorded"));
    }
    @Test void appOpenCountsAnAnonymousClientWithoutPoiOrAudio() {
        var input = new PlaybackEventController.Input(null, null, "APP_OPEN", "new-guest", "visit-1");
        assertTrue(controller.record(input).get("recorded"));
        assertFalse(controller.record(input).get("recorded"));
        var saved = org.mockito.ArgumentCaptor.forClass(UsageEvent.class);
        verify(events).saveAndFlush(saved.capture());
        assertEquals("APP_OPEN", saved.getValue().getEventType());
        assertEquals("new-guest", saved.getValue().getAnonClientId());
        assertEquals("visit-1", saved.getValue().getPlaybackId());
        assertNull(saved.getValue().getPoiId());
        verifyNoInteractions(pois, languages);
    }
    @Test void databaseDeduplicatesAnEventAfterBackendRestart() {
        when(events.existsByAnonClientIdAndPlaybackIdAndEventType("guest", "visit-1", "APP_OPEN")).thenReturn(true);
        var input = new PlaybackEventController.Input(null, null, "APP_OPEN", "guest", "visit-1");
        assertFalse(new PlaybackEventController(pois, languages, events).record(input).get("recorded"));
        verify(events, never()).saveAndFlush(any());
    }
    @Test void concurrentDuplicateWriteDoesNotCountTwice() {
        when(events.existsByAnonClientIdAndPlaybackIdAndEventType("guest", "visit-1", "APP_OPEN"))
                .thenReturn(false, true);
        when(events.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate playback event"));
        var input = new PlaybackEventController.Input(null, null, "APP_OPEN", "guest", "visit-1");
        assertFalse(controller.record(input).get("recorded"));
    }
    @Test void appOpenCannotPretendToBeAPoiListen() {
        var input = new PlaybackEventController.Input(1L, "vi", "APP_OPEN", "guest", "visit-1");
        assertThrows(IllegalArgumentException.class, () -> controller.record(input));
        verifyNoInteractions(events);
    }
}
