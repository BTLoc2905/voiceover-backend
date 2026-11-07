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
    final RouteRepository routes = mock(RouteRepository.class);
    final SessionRepository sessions = mock(SessionRepository.class);
    final PlaybackEventController controller = new PlaybackEventController(pois, languages, events, routes, sessions);
    @Test void recordsActualStartOncePerPlaybackAndCompletionSeparately() {
        when(pois.findById(1L)).thenReturn(Optional.of(Poi.builder().id(1L).status("PUBLISHED").build()));
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        var start = new PlaybackEventController.Input(1L, null, "vi", "AUTO_PLAY", "client", "playback-1");
        assertTrue(controller.record(start).get("recorded"));
        assertFalse(controller.record(start).get("recorded"));
        assertTrue(controller.record(new PlaybackEventController.Input(1L, null, "vi", "AUDIO_COMPLETED", "client", "playback-1")).get("recorded"));
        verify(events, times(2)).saveAndFlush(any());
    }
    @Test void rejectsPreloadUnknownPoiAndInactiveLanguageWithoutRecording() {
        assertThrows(IllegalArgumentException.class, () -> controller.record(new PlaybackEventController.Input(1L, null, "vi", "PRELOAD", "client", "p")));
        assertThrows(IllegalArgumentException.class, () -> controller.record(new PlaybackEventController.Input(1L, null, "vi", "AUTO_PLAY", "client", "p")));
        when(pois.findById(1L)).thenReturn(Optional.of(Poi.builder().id(1L).status("PUBLISHED").build()));
        assertThrows(IllegalArgumentException.class, () -> controller.record(new PlaybackEventController.Input(1L, null, "vi", "AUTO_PLAY", "client", "p")));
        verifyNoInteractions(events);
    }
    @Test void retryAfterFailedWriteCanStillRecord() {
        when(pois.findById(1L)).thenReturn(Optional.of(Poi.builder().id(1L).status("PUBLISHED").build()));
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        when(events.saveAndFlush(any())).thenThrow(new IllegalStateException("DB failed")).thenReturn(new UsageEvent());
        var input = new PlaybackEventController.Input(1L, null, "vi", "TAP_POI", "client", "p");
        assertThrows(IllegalStateException.class, () -> controller.record(input));
        assertTrue(controller.record(input).get("recorded"));
    }
    @Test void appOpenCountsAnAnonymousClientWithoutPoiOrAudio() {
        var input = new PlaybackEventController.Input(null, null, null, "APP_OPEN", "new-guest", "visit-1");
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
        var input = new PlaybackEventController.Input(null, null, null, "APP_OPEN", "guest", "visit-1");
        assertFalse(new PlaybackEventController(pois, languages, events, routes, sessions).record(input).get("recorded"));
        verify(events, never()).saveAndFlush(any());
    }
    @Test void concurrentDuplicateWriteDoesNotCountTwice() {
        when(events.existsByAnonClientIdAndPlaybackIdAndEventType("guest", "visit-1", "APP_OPEN"))
                .thenReturn(false, true);
        when(events.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate playback event"));
        var input = new PlaybackEventController.Input(null, null, null, "APP_OPEN", "guest", "visit-1");
        assertFalse(controller.record(input).get("recorded"));
    }
    @Test void appOpenCannotPretendToBeAPoiListen() {
        var input = new PlaybackEventController.Input(1L, null, "vi", "APP_OPEN", "guest", "visit-1");
        assertThrows(IllegalArgumentException.class, () -> controller.record(input));
        verifyNoInteractions(events);
    }

    @Test void heartbeatCountsOnlyOncePerIdAndCannotCarryPoi() {
        var input = new PlaybackEventController.Input(null, null, null, "APP_ACTIVE", "guest", "heartbeat-1");
        assertTrue(controller.record(input).get("recorded"));
        assertFalse(controller.record(input).get("recorded"));
        assertThrows(IllegalArgumentException.class, () -> controller.record(
                new PlaybackEventController.Input(1L, null, null, "APP_ACTIVE", "guest", "heartbeat-2")));
        verify(events, times(1)).saveAndFlush(any());
    }

    @Test void routeSelectionRequiresPublishedRouteAndSession() {
        var input = new PlaybackEventController.Input(null, 7L, null, "ROUTE_SELECTED", "guest", "start-1");
        assertThrows(IllegalArgumentException.class, () -> controller.record(input));
        when(routes.findById(7L)).thenReturn(Optional.of(Route.builder().id(7L).sessionId(3L).status("PUBLISHED").build()));
        assertThrows(IllegalArgumentException.class, () -> controller.record(input));
        when(sessions.findById(3L)).thenReturn(Optional.of(Session.builder().id(3L).status("PUBLISHED").build()));
        assertTrue(controller.record(input).get("recorded"));
        assertFalse(controller.record(input).get("recorded"));
        var saved = org.mockito.ArgumentCaptor.forClass(UsageEvent.class);
        verify(events).saveAndFlush(saved.capture());
        assertEquals(7L, saved.getValue().getRouteId());
        assertEquals("ROUTE_SELECTED", saved.getValue().getEventType());
    }
}
