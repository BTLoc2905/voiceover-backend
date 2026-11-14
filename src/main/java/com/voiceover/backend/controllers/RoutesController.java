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
public class RoutesController {
    private final RouteService service;
    public record RouteInput(String name, String description, String status, List<Long> poiIds) {}
    public record StartInput(List<Long> poiIds) {}

    @GetMapping("/admin/routes")
    public Object routes() { return service.listRoutes(); }

    @GetMapping("/admin/routes/{routeId}")
    public Object route(@PathVariable Long routeId) { return service.getRoute(routeId); }

    @PostMapping("/admin/routes")
    public Object createRoute(@RequestBody RouteInput input) {
        return service.routeView(service.createRoute(input.name(), input.description(), input.status(), input.poiIds()));
    }

    @PutMapping("/admin/routes/{routeId}")
    public Object updateRoute(@PathVariable Long routeId, @RequestBody RouteInput input) {
        return service.routeView(service.updateRoute(routeId, input.name(), input.description(), input.status(), input.poiIds()));
    }

    @DeleteMapping("/admin/routes/{routeId}")
    public ResponseEntity<Void> deleteRoute(@PathVariable Long routeId) {
        service.deleteRoute(routeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/routes/{routeId}")
    public Object publicRoute(@PathVariable Long routeId) { return service.startRoute(routeId, null); }

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
