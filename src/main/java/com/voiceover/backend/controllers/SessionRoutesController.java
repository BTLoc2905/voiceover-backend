package com.voiceover.backend.controllers;

import com.voiceover.backend.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionRoutesController {
    private final RouteSessionService service;
    public record SessionInput(String name, String description, String status) {}
    public record RouteInput(String name, String status, List<Long> poiIds) {}
    public record StartInput(List<Long> poiIds) {}

    @GetMapping("/admin/sessions")
    public Object sessions() { return service.listSessions(); }

    @PostMapping("/admin/sessions")
    public Object createSession(@RequestBody SessionInput input) {
        return service.sessionView(service.saveSession(null, input.name(), input.description(), input.status()));
    }

    @PutMapping("/admin/sessions/{id}")
    public Object updateSession(@PathVariable Long id, @RequestBody SessionInput input) {
        return service.sessionView(service.saveSession(id, input.name(), input.description(), input.status()));
    }

    @PostMapping(value = "/admin/sessions/{sessionId}/routes", params = "!name")
    public Object createRoute(@PathVariable Long sessionId, @RequestBody RouteInput input) {
        return service.routeView(service.saveRouteWithStops(sessionId, input.name(), input.status(), input.poiIds()));
    }

    @PutMapping("/admin/sessions/{sessionId}/routes/{routeId}")
    public Object updateRoute(@PathVariable Long sessionId, @PathVariable Long routeId, @RequestBody RouteInput input) {
        return service.routeView(service.updateRoute(sessionId, routeId, input.name(), input.status(), input.poiIds()));
    }

    @GetMapping("/admin/pois/{poiId}/coverage")
    public Object coverage(@PathVariable Long poiId) { return service.poiCoverage(poiId); }

    @PostMapping("/public/routes/{routeId}/start")
    public ResponseEntity<?> start(@PathVariable Long routeId, @RequestBody(required = false) StartInput input) {
        try { return ResponseEntity.ok(service.startRoute(routeId, input == null ? null : input.poiIds())); }
        catch (WalkingDirectionsService.NoWalkingRouteException error) { return ResponseEntity.status(422).body(Map.of("message", error.getMessage())); }
        catch (IllegalStateException error) { return ResponseEntity.status(503).body(Map.of("message", error.getMessage())); }
    }

    @GetMapping("/public/routes/{routeId}/leg")
    public Object leg(@PathVariable Long routeId, @RequestParam Long targetId, @RequestParam double lat, @RequestParam double lng) {
        return service.walkingLeg(routeId, targetId, lat, lng);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> routingUnavailable(IllegalStateException error) {
        return ResponseEntity.status(error instanceof WalkingDirectionsService.NoWalkingRouteException ? 422 : 503)
                .body(Map.of("message", error.getMessage()));
    }
}
