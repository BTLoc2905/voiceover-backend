package com.voiceover.backend.controllers;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.Test;
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
}
