package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionPublicationTest {
    final SessionRepository sessions = mock(SessionRepository.class);
    final RouteRepository routes = mock(RouteRepository.class);
    final RouteStopRepository stops = mock(RouteStopRepository.class);
    final PoiRepository pois = mock(PoiRepository.class);
    final WalkingDirectionsService directions = mock(WalkingDirectionsService.class);
    RouteSessionService service;
    Session session;
    @BeforeEach void prepare() {
        service = new RouteSessionService(sessions, routes, stops, pois, directions,
                mock(AudioRepository.class), mock(TranslationRepository.class), mock(LanguageRepository.class));
        session = Session.builder().id(1L).name("Tour session").status("DRAFT").build();
        when(sessions.findForUpdate(1L)).thenReturn(Optional.of(session));
        when(sessions.findById(1L)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(routes.saveAndFlush(any())).thenAnswer(call -> { Route r = call.getArgument(0); if (r.getId() == null) r.setId(10L); return r; });
        LongStream.rangeClosed(1, 11).forEach(id -> {
            Poi poi = Poi.builder().id(id).name("POI " + id).status("PUBLISHED")
                    .latitude(new BigDecimal("10.77")).longitude(new BigDecimal("106.68")).build();
            when(pois.findForUpdate(id)).thenReturn(Optional.of(poi));
            when(pois.findById(id)).thenReturn(Optional.of(poi));
        });
    }
    Route existing(String status, List<Long> ids) {
        Route route = Route.builder().id(10L).sessionId(1L).name("Route test").status(status).build();
        when(routes.findById(10L)).thenReturn(Optional.of(route));
        when(routes.findBySessionId(1L)).thenReturn(List.of(route));
        List<RouteStop> sequence = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) sequence.add(RouteStop.builder().routeId(10L).poiId(ids.get(i)).stopOrder(i + 1).build());
        when(stops.findByRouteIdOrderByStopOrderAsc(10L)).thenReturn(sequence);
        return route;
    }
    @Test void incompleteAndOversizedDraftsAreSavedWithoutRouting() {
        for (List<Long> ids : List.of(List.<Long>of(), List.of(1L), List.of(1L, 1L), LongStream.rangeClosed(1, 11).boxed().toList())) {
            assertEquals("DRAFT", service.saveRouteWithStops(1L, "", null, ids).getStatus());
        }
        verifyNoInteractions(directions);
    }
    @Test void draftCanReferenceUnpublishedPoiButPublicationCannot() {
        pois.findById(2L).orElseThrow().setStatus("DRAFT");
        assertDoesNotThrow(() -> service.saveRouteWithStops(1L, "", "DRAFT", List.of(1L, 2L)));
        assertThrows(IllegalArgumentException.class, () -> service.saveRouteWithStops(1L, "Tour", "PUBLISHED", List.of(1L, 2L)));
    }
    @Test void publicationRejectsCountsDuplicatesMissingCoordinatesAndEmptyName() {
        for (List<Long> ids : List.of(List.<Long>of(), List.of(1L), List.of(1L, 1L), LongStream.rangeClosed(1, 11).boxed().toList())) {
            assertThrows(IllegalArgumentException.class, () -> service.saveRouteWithStops(1L, "Tour", "PUBLISHED", ids));
        }
        assertThrows(IllegalArgumentException.class, () -> service.saveRouteWithStops(1L, "", "PUBLISHED", List.of(1L, 2L)));
        pois.findById(2L).orElseThrow().setLongitude(null);
        assertThrows(IllegalArgumentException.class, () -> service.saveRouteWithStops(1L, "Tour", "PUBLISHED", List.of(1L, 2L)));
        verify(routes, never()).saveAndFlush(any());
    }
    @Test void missingSessionAndMissingPoiNeverCreateRoute() {
        assertThrows(IllegalArgumentException.class, () -> service.saveRouteWithStops(99L, "Tour", "DRAFT", List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.saveRouteWithStops(1L, "Tour", "DRAFT", List.of(99L)));
        verify(routes, never()).saveAndFlush(any());
    }
    @Test void sessionNeedsPublishedRouteAndDoesNotPublishDraftChildren() {
        Route draft = existing("DRAFT", List.of(1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> service.saveSession(1L, "Session", "", "PUBLISHED"));
        Route published = Route.builder().id(11L).sessionId(1L).name("Ready").status("PUBLISHED").build();
        when(routes.findBySessionId(1L)).thenReturn(List.of(draft, published));
        List<RouteStop> sequence = stops.findByRouteIdOrderByStopOrderAsc(10L);
        when(stops.findByRouteIdOrderByStopOrderAsc(11L)).thenReturn(sequence);
        assertEquals("PUBLISHED", service.saveSession(1L, "Session", "", "PUBLISHED").getStatus());
        assertEquals("DRAFT", draft.getStatus());
        verify(routes, never()).save(any());
    }
    @Test void closingSessionRetainsRoutesAndStops() {
        session.setStatus("PUBLISHED"); existing("PUBLISHED", List.of(1L, 2L));
        assertEquals("DRAFT", service.saveSession(1L, "Session", "", "DRAFT").getStatus());
        verifyNoInteractions(stops); verify(routes, never()).delete(any());
    }
    @Test void cannotUnpublishLastRouteOfPublishedSession() {
        session.setStatus("PUBLISHED"); existing("PUBLISHED", List.of(1L, 2L));
        assertThrows(RouteConflictException.class, () -> service.updateRoute(1L, 10L, "Tour", "DRAFT", List.of()));
        verify(stops, never()).deleteByRouteId(any());
    }
    @Test void updatingRouteFlushesOldStopsAndPersistsExactNewOrder() {
        existing("DRAFT", List.of(1L, 2L));
        service.updateRoute(1L, 10L, "Edited", "PUBLISHED", List.of(2L, 1L));
        ArgumentCaptor<RouteStop> sequence = ArgumentCaptor.forClass(RouteStop.class);
        var order = inOrder(stops);
        order.verify(stops).deleteByRouteId(10L); order.verify(stops).flush();
        order.verify(stops, times(2)).save(sequence.capture()); order.verify(stops).flush();
        assertEquals(List.of(2L, 1L), sequence.getAllValues().stream().map(RouteStop::getPoiId).toList());
        assertEquals(List.of(1, 2), sequence.getAllValues().stream().map(RouteStop::getStopOrder).toList());
    }
    @Test void startRejectsClosedParentAndRevalidatesPersonalPois() {
        Route route = existing("PUBLISHED", List.of(1L, 2L));
        assertThrows(RouteConflictException.class, () -> service.startRoute(10L, null));
        session.setStatus("PUBLISHED"); route.setStatus("DRAFT");
        assertThrows(RouteConflictException.class, () -> service.startRoute(10L, null));
        route.setStatus("PUBLISHED"); pois.findById(3L).orElseThrow().setStatus("HIDDEN");
        assertThrows(IllegalArgumentException.class, () -> service.startRoute(10L, List.of(1L, 3L)));
        verifyNoInteractions(directions);
    }
    @Test void startUsesPublishedOrderedPoisAndRealWalkingResult() {
        existing("PUBLISHED", List.of(2L, 1L)); session.setStatus("PUBLISHED");
        when(directions.calculate(anyList())).thenReturn(new WalkingDirectionsService.WalkingRoute(900, 650, Map.of("type", "LineString"), List.of()));
        Map<String, Object> result = service.startRoute(10L, null);
        assertEquals(900L, result.get("totalDistanceMeters")); assertEquals(10L, result.get("routeId"));
        assertEquals(List.of(pois.findById(2L).orElseThrow(), pois.findById(1L).orElseThrow()), result.get("pois"));
    }
}
