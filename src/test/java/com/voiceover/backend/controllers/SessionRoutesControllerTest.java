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

class SessionRoutesControllerTest {
    final RouteSessionService service = mock(RouteSessionService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new SessionRoutesController(service)).setControllerAdvice(new RouteErrorHandler()).build();
    }
    @Test void jsonRouteInputPreservesDraftAndOrder() throws Exception {
        Route route = Route.builder().id(8L).sessionId(3L).name("").status("DRAFT").build();
        when(service.saveRouteWithStops(3L, "", "DRAFT", List.of(2L))).thenReturn(route);
        when(service.routeView(route)).thenReturn(Map.of("id", 8L, "poiIds", List.of(2L), "status", "DRAFT"));
        mvc.perform(post("/api/admin/sessions/3/routes").contentType("application/json")
                .content("{\"name\":\"\",\"status\":\"DRAFT\",\"poiIds\":[2]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.poiIds[0]").value(2));
        verify(service).saveRouteWithStops(3L, "", "DRAFT", List.of(2L));
    }
    @Test void validationIs400AndBusinessConflictIs409WithMessage() throws Exception {
        when(service.saveSession(3L, "Session", "", "PUBLISHED")).thenThrow(new IllegalArgumentException("Cần một Route đã xuất bản"));
        mvc.perform(put("/api/admin/sessions/3").contentType("application/json").content("{\"name\":\"Session\",\"description\":\"\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cần một Route đã xuất bản"));
        when(service.startRoute(8L, null)).thenThrow(new RouteConflictException("Session đã tạm đóng"));
        mvc.perform(post("/api/public/routes/8/start")).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Session đã tạm đóng"));
    }
    @Test void startAndLegExposeWalkingErrorsWithoutFakeMetrics() throws Exception {
        when(service.startRoute(8L, List.of(2L, 1L))).thenThrow(new WalkingDirectionsService.NoWalkingRouteException());
        mvc.perform(post("/api/public/routes/8/start").contentType("application/json").content("{\"poiIds\":[2,1]}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.totalDistanceMeters").doesNotExist());
        when(service.walkingLeg(8L, 2L, 10.7, 106.6)).thenThrow(new IllegalStateException("Directions không khả dụng"));
        mvc.perform(get("/api/public/routes/8/leg?targetId=2&lat=10.7&lng=106.6"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Directions không khả dụng"));
    }

    @Test void deleteEndpointsReturnNoContentAndExposeConflicts() throws Exception {
        mvc.perform(delete("/api/admin/sessions/3/routes/8")).andExpect(status().isNoContent());
        verify(service).deleteRoute(3L, 8L);
        mvc.perform(delete("/api/admin/sessions/3")).andExpect(status().isNoContent());
        verify(service).deleteSession(3L);
        doThrow(new RouteConflictException("Hãy chuyển Session về Nháp trước khi xóa."))
                .when(service).deleteSession(4L);
        mvc.perform(delete("/api/admin/sessions/4"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Hãy chuyển Session về Nháp trước khi xóa."));
    }
}
