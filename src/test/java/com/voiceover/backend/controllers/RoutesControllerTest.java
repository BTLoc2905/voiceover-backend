package com.voiceover.backend.controllers;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoutesControllerTest {
    final RouteService service = mock(RouteService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new RoutesController(service)).setControllerAdvice(new RouteErrorHandler()).build();
    }
    @Test void jsonRouteInputPreservesDescriptionDraftAndOrderWithoutSession() throws Exception {
        Route route = Route.builder().id(8L).name("").description("Description").status("DRAFT").build();
        when(service.createRoute("", "Description", null, List.of(2L))).thenReturn(route);
        when(service.routeView(route)).thenReturn(Map.of("id", 8L, "description", "Description", "poiIds", List.of(2L), "status", "DRAFT"));
        mvc.perform(post("/api/admin/routes").contentType("application/json")
                .content("{\"name\":\"\",\"description\":\"Description\",\"poiIds\":[2]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.poiIds[0]").value(2)).andExpect(jsonPath("$.description").value("Description"))
                .andExpect(jsonPath("$.sessionId").doesNotExist());
        verify(service).createRoute("", "Description", null, List.of(2L));
    }
    @Test void validationIs400AndBusinessConflictIs409WithMessage() throws Exception {
        when(service.updateRoute(3L, "Route", "", "PUBLISHED", List.of(1L))).thenThrow(new IllegalArgumentException("Cần 2–10 POI"));
        mvc.perform(put("/api/admin/routes/3").contentType("application/json").content("{\"name\":\"Route\",\"description\":\"\",\"status\":\"PUBLISHED\",\"poiIds\":[1]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cần 2–10 POI"));
        when(service.startRoute(8L, null)).thenThrow(new RouteConflictException("Route đã ẩn"));
        mvc.perform(post("/api/public/routes/8/start")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Route đã ẩn"));
    }
    @Test void startAndLegExposeWalkingErrorsWithoutFakeMetrics() throws Exception {
        when(service.startRoute(8L, List.of(2L, 1L))).thenThrow(new WalkingDirectionsService.NoWalkingRouteException());
        mvc.perform(post("/api/public/routes/8/start").contentType("application/json").content("{\"poiIds\":[2,1]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.totalDistanceMeters").doesNotExist());
        when(service.walkingLeg(8L, 2L, 10.7, 106.6)).thenThrow(new IllegalStateException("Directions không khả dụng"));
        mvc.perform(get("/api/public/routes/8/leg?targetId=2&lat=10.7&lng=106.6"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Directions không khả dụng"));
    }
    @Test void listDetailAndDeleteUseDirectRouteEndpoints() throws Exception {
        when(service.listRoutes()).thenReturn(List.of(Map.of("id", 8L)));
        when(service.getRoute(8L)).thenReturn(Map.of("id", 8L, "description", "Description"));
        mvc.perform(get("/api/admin/routes")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(8));
        mvc.perform(get("/api/admin/routes/8")).andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Description"));
        mvc.perform(delete("/api/admin/routes/8")).andExpect(status().isNoContent());
        verify(service).deleteRoute(8L);
        mvc.perform(get("/api/admin/sessions")).andExpect(status().isNotFound());
    }
    @Test void publicDetailIsAnonymousAndIncludesWalkingSummary() throws Exception {
        when(service.startRoute(8L, null)).thenReturn(Map.of("routeId", 8L, "totalDistanceMeters", 900, "estimatedDurationMinutes", 11));
        mvc.perform(get("/api/public/routes/8")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDistanceMeters").value(900)).andExpect(jsonPath("$.estimatedDurationMinutes").value(11));
    }
}
