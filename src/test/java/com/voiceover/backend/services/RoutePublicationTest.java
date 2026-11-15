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

class RoutePublicationTest {
    final RouteRepository routes = mock(RouteRepository.class);
    final RouteStopRepository stops = mock(RouteStopRepository.class);
    final PoiRepository pois = mock(PoiRepository.class);
    final WalkingDirectionsService directions = mock(WalkingDirectionsService.class);
    RouteService service;

    @BeforeEach void prepare() {
        service = new RouteService(routes, stops, pois, directions,
                mock(AudioRepository.class), mock(TranslationRepository.class), mock(LanguageRepository.class));
        when(routes.saveAndFlush(any())).thenAnswer(call -> { Route r = call.getArgument(0); if (r.getId() == null) r.setId(10L); return r; });
        LongStream.rangeClosed(1, 11).forEach(id -> {
            Poi poi = Poi.builder().id(id).name("POI " + id).status("PUBLISHED")
                    .latitude(new BigDecimal("10.77")).longitude(new BigDecimal("106.68")).build();
            when(pois.findForUpdate(id)).thenReturn(Optional.of(poi));
            when(pois.findById(id)).thenReturn(Optional.of(poi));
        });
    }

    Route existing(String status, List<Long> ids) {
        Route route = Route.builder().id(10L).name("Route test").description("Description").status(status).build();
        when(routes.findById(10L)).thenReturn(Optional.of(route));
        when(routes.findForUpdate(10L)).thenReturn(Optional.of(route));
        List<RouteStop> sequence = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) sequence.add(RouteStop.builder().routeId(10L).poiId(ids.get(i)).stopOrder(i + 1).build());
        when(stops.findByRouteIdOrderByStopOrderAsc(10L)).thenReturn(sequence);
        return route;
    }

    @Test void incompleteDraftsDefaultToDraftWithoutAnyTourSession() {
        for (List<Long> ids : List.of(List.<Long>of(), List.of(1L), LongStream.rangeClosed(1, 11).boxed().toList())) {
            Route saved = service.createRoute("", "Draft description", null, ids);
            assertEquals("DRAFT", saved.getStatus());
            assertEquals("Draft description", saved.getDescription());
        }
        verifyNoInteractions(directions);
    }

    @Test void draftCanReferenceUnpublishedPoiButPublicationCannot() {
        for (String status : List.of("DRAFT", "HIDDEN")) {
            pois.findById(2L).orElseThrow().setStatus(status);
            assertDoesNotThrow(() -> service.createRoute("", "", "DRAFT", List.of(1L, 2L)));
            assertThrows(IllegalArgumentException.class, () -> service.createRoute("Tour", "", "PUBLISHED", List.of(1L, 2L)));
        }
    }

    @Test void publicationRejectsInvalidCountsDuplicatesCoordinatesAndEmptyName() {
        for (List<Long> ids : List.of(List.<Long>of(), List.of(1L), List.of(1L, 1L), LongStream.rangeClosed(1, 11).boxed().toList())) {
            assertThrows(IllegalArgumentException.class, () -> service.createRoute("Tour", "", "PUBLISHED", ids));
        }
        assertThrows(IllegalArgumentException.class, () -> service.createRoute("", "", "PUBLISHED", List.of(1L, 2L)));
        pois.findById(2L).orElseThrow().setLongitude(null);
        assertThrows(IllegalArgumentException.class, () -> service.createRoute("Tour", "", "PUBLISHED", List.of(1L, 2L)));
        verify(routes, never()).saveAndFlush(any());
    }

    @Test void publishesTwoAndTenPoisAndRejectsUnknownStatusesOrMissingPois() {
        for (List<Long> ids : List.of(List.of(1L, 2L), LongStream.rangeClosed(1, 10).boxed().toList())) {
            assertEquals("PUBLISHED", service.createRoute("Tour", "Description", "PUBLISHED", ids).getStatus());
        }
        assertThrows(IllegalArgumentException.class, () -> service.createRoute("Tour", "", "CLOSED", List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.createRoute("Tour", "", "DRAFT", List.of(99L)));
        assertThrows(IllegalArgumentException.class, () -> service.updateRoute(99L, "Tour", "", "DRAFT", List.of()));
    }

    @Test void publishedRouteCanBeHiddenIndependentlyAndKeepsItsStops() {
        Route route = existing("PUBLISHED", List.of(2L, 1L));
        assertEquals("DRAFT", service.updateRoute(10L, "Tour", "Keep description", "DRAFT", List.of(2L, 1L)).getStatus());
        assertEquals("Keep description", route.getDescription());
        verify(pois, never()).delete(any());
    }

    @Test void publishedEditsAreRevalidatedBeforeAnyDatabaseWrites() {
        existing("PUBLISHED", List.of(1L, 2L));
        pois.findById(2L).orElseThrow().setStatus("HIDDEN");
        assertThrows(IllegalArgumentException.class, () -> service.updateRoute(10L, "Edited", "", "PUBLISHED", List.of(2L, 1L)));
        verify(routes, never()).saveAndFlush(any());
        verify(stops, never()).deleteByRouteId(any());
    }

    @Test void updatingFlushesOldStopsAndPersistsExactNewOrder() {
        existing("DRAFT", List.of(1L, 2L));
        service.updateRoute(10L, "Edited", "New description", "PUBLISHED", List.of(2L, 1L));
        ArgumentCaptor<RouteStop> sequence = ArgumentCaptor.forClass(RouteStop.class);
        var order = inOrder(stops);
        order.verify(stops).deleteByRouteId(10L); order.verify(stops).flush();
        order.verify(stops, times(2)).save(sequence.capture()); order.verify(stops).flush();
        assertEquals(List.of(2L, 1L), sequence.getAllValues().stream().map(RouteStop::getPoiId).toList());
        assertEquals(List.of(1, 2), sequence.getAllValues().stream().map(RouteStop::getStopOrder).toList());
    }

    @Test void startRejectsDraftAndRevalidatesPersonalPois() {
        Route route = existing("DRAFT", List.of(1L, 2L));
        assertThrows(RouteConflictException.class, () -> service.startRoute(10L, null));
        route.setStatus("PUBLISHED"); pois.findById(3L).orElseThrow().setStatus("HIDDEN");
        assertThrows(IllegalArgumentException.class, () -> service.startRoute(10L, List.of(1L, 3L)));
        verifyNoInteractions(directions);
    }

    @Test void personalStartUsesWalkingResultWithoutChangingOriginalRoute() {
        existing("PUBLISHED", List.of(2L, 1L));
        when(directions.calculate(anyList())).thenReturn(new WalkingDirectionsService.WalkingRoute(900, 650, Map.of("type", "LineString"), List.of()));
        Map<String, Object> result = service.startRoute(10L, List.of(3L, 1L));
        assertEquals(900L, result.get("totalDistanceMeters")); assertEquals(10L, result.get("routeId"));
        assertFalse(result.containsKey("sessionId"));
        assertEquals(List.of(pois.findById(3L).orElseThrow(), pois.findById(1L).orElseThrow()), result.get("pois"));
        verify(routes, never()).saveAndFlush(any());
        verify(stops, never()).deleteByRouteId(any());
        assertEquals(List.of(2L, 1L), stops.findByRouteIdOrderByStopOrderAsc(10L).stream().map(RouteStop::getPoiId).toList());
    }
}
