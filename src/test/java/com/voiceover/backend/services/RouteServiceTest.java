package com.voiceover.backend.services;

import com.voiceover.backend.entities.Poi;
import com.voiceover.backend.entities.Route;
import com.voiceover.backend.entities.RouteStop;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RouteServiceTest {
    private final PoiRepository pois = mock(PoiRepository.class);
    private final RouteRepository routes = mock(RouteRepository.class);
    private final RouteStopRepository stops = mock(RouteStopRepository.class);
    private final WalkingDirectionsService directions = mock(WalkingDirectionsService.class);
    private RouteService service;

    @BeforeEach
    void setUp() {
        service = new RouteService(routes, stops, pois, directions,
                mock(AudioRepository.class), mock(TranslationRepository.class), mock(LanguageRepository.class));
    }

    private Poi poi(long id) {
        return Poi.builder().id(id).name("POI " + id).status("PUBLISHED")
                .latitude(new BigDecimal("10.77")).longitude(new BigDecimal("106.68")).build();
    }

    @Test
    void walkingLegUsesActualGpsAndReturnsDirectionsTimeWithoutWritingProgress() {
        when(routes.findById(10L)).thenReturn(Optional.of(Route.builder().id(10L).name("Route").status("PUBLISHED").build()));
        when(stops.findByRouteIdOrderByStopOrderAsc(10L)).thenReturn(List.of(
                RouteStop.builder().poiId(1L).stopOrder(1).build(), RouteStop.builder().poiId(2L).stopOrder(2).build()));
        when(pois.findById(1L)).thenReturn(Optional.of(poi(1)));
        when(pois.findById(2L)).thenReturn(Optional.of(poi(2)));
        when(directions.calculate(anyList())).thenAnswer(invocation -> {
            List<Poi> input = invocation.getArgument(0);
            assertEquals(10.75, input.getFirst().getLatitude().doubleValue());
            assertEquals(106.65, input.getFirst().getLongitude().doubleValue());
            assertEquals(2L, input.getLast().getId());
            return new WalkingDirectionsService.WalkingRoute(1500, 721, Map.of("type", "LineString"), List.of());
        });
        var leg = service.walkingLeg(10L, 2L, 10.75, 106.65);
        assertEquals(721, leg.get("durationSeconds")); assertEquals(13, leg.get("estimatedDurationMinutes"));
        assertEquals(1500L, leg.get("totalDistanceMeters"));
        verify(pois, never()).save(any()); verify(routes, never()).save(any()); verify(stops, never()).save(any());
        assertThrows(IllegalArgumentException.class, () -> service.walkingLeg(10L, 2L, Double.NaN, 106));
    }

    @Test
    void rejectsInvalidCountsAndDuplicateOrMissingIdsBeforeRouting() {
        for (List<Long> ids : List.of(List.of(1L), LongStream.rangeClosed(1, 11).boxed().toList(), List.of(1L, 1L), List.of(0L, 2L))) {
            assertThrows(IllegalArgumentException.class, () -> service.validateAndCalculateCustomRoute(ids));
        }
        assertThrows(IllegalArgumentException.class, () -> service.validateAndCalculateCustomRoute(null));
        verifyNoInteractions(directions, pois);
    }

    @Test
    void preservesRequestedOrderAndReturnsActualWalkingMetrics() {
        Poi first = poi(1), second = poi(2);
        when(pois.findById(1L)).thenReturn(Optional.of(first));
        when(pois.findById(2L)).thenReturn(Optional.of(second));
        when(directions.calculate(List.of(second, first))).thenReturn(new WalkingDirectionsService.WalkingRoute(
                1432, 1201, Map.of("type", "LineString"), List.of(Map.of("distanceMeters", 1432L))));
        Map<String, Object> result = service.validateAndCalculateCustomRoute(List.of(2L, 1L));
        assertEquals(List.of(second, first), result.get("pois"));
        assertEquals(1432L, result.get("totalDistanceMeters"));
        assertEquals(21, result.get("estimatedDurationMinutes"));
        assertEquals("WALKING", result.get("travelMode"));
        verify(directions).calculate(List.of(second, first));
    }

    @Test
    void acceptsExactlyTenStops() {
        List<Long> ids = LongStream.rangeClosed(1, 10).boxed().toList();
        ids.forEach(id -> when(pois.findById(id)).thenReturn(Optional.of(poi(id))));
        when(directions.calculate(anyList())).thenReturn(new WalkingDirectionsService.WalkingRoute(1000, 600, Map.of(), List.of()));
        assertEquals(10, service.validateAndCalculateCustomRoute(ids).get("stopCount"));
    }

    @Test
    void refusesHiddenMissingAndInvalidCoordinatePois() {
        when(pois.findById(1L)).thenReturn(Optional.of(poi(1)));
        Poi unavailable = poi(2);
        unavailable.setStatus("HIDDEN");
        when(pois.findById(2L)).thenReturn(Optional.of(unavailable));
        assertThrows(IllegalArgumentException.class, () -> service.validateAndCalculateCustomRoute(List.of(1L, 2L)));
        unavailable.setStatus("PUBLISHED");
        unavailable.setLatitude(new BigDecimal("91"));
        assertThrows(IllegalArgumentException.class, () -> service.validateAndCalculateCustomRoute(List.of(1L, 2L)));
        unavailable.setLatitude(null);
        assertThrows(IllegalArgumentException.class, () -> service.validateAndCalculateCustomRoute(List.of(1L, 2L)));
        when(pois.findById(2L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.validateAndCalculateCustomRoute(List.of(1L, 2L)));
        verifyNoInteractions(directions);
    }

    @Test
    void neverSubstitutesStraightLineMetricsWhenDirectionsFails() {
        when(pois.findById(1L)).thenReturn(Optional.of(poi(1)));
        when(pois.findById(2L)).thenReturn(Optional.of(poi(2)));
        when(directions.calculate(anyList())).thenThrow(new WalkingDirectionsService.NoWalkingRouteException());
        assertThrows(WalkingDirectionsService.NoWalkingRouteException.class,
                () -> service.validateAndCalculateCustomRoute(List.of(1L, 2L)));
    }

    @Test
    void suggestedRoutesRequireOnlyPublishedRouteAndUsableOrderedStops() {
        Route available = Route.builder().id(10L).name("Tour").status("PUBLISHED").build();
        Route invalidRoute = Route.builder().id(11L).name("Incomplete route").status("PUBLISHED").build();
        when(routes.findByStatus("PUBLISHED")).thenReturn(List.of(available, invalidRoute));
        when(stops.findByRouteIdOrderByStopOrderAsc(10L)).thenReturn(List.of(
                RouteStop.builder().poiId(2L).stopOrder(1).build(), RouteStop.builder().poiId(1L).stopOrder(2).build()));
        Poi a = poi(1), b = poi(2);
        when(pois.findById(1L)).thenReturn(Optional.of(a));
        when(pois.findById(2L)).thenReturn(Optional.of(b));
        assertEquals(List.of(b, a), service.getSuggestedRoutes().getFirst().get("pois"));
        assertEquals(1, service.getSuggestedRoutes().size());
        b.setStatus("DRAFT");
        assertTrue(service.getSuggestedRoutes().isEmpty());
        verifyNoInteractions(directions);
    }

    @Test
    void deletingRouteRemovesOnlyItsStopsAndRoute() {
        Route route = Route.builder().id(8L).status("PUBLISHED").build();
        when(routes.findForUpdate(8L)).thenReturn(Optional.of(route));
        service.deleteRoute(8L);
        var order = inOrder(stops, routes);
        order.verify(stops).deleteByRouteId(8L);
        order.verify(stops).flush();
        order.verify(routes).delete(route);
        verifyNoInteractions(pois);
        assertThrows(IllegalArgumentException.class, () -> service.deleteRoute(99L));
    }
}
