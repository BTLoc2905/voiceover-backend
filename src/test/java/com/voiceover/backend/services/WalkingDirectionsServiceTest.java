package com.voiceover.backend.services;

import com.sun.net.httpserver.HttpServer;
import com.voiceover.backend.entities.Poi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WalkingDirectionsServiceTest {
    private HttpServer server;
    private WalkingDirectionsService service;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private int status = 200;
    private String body = """
            {"code":"Ok","routes":[{"distance":1500.5,"duration":1000.1,
            "geometry":{"type":"LineString","coordinates":[[106.68,10.77],[106.69,10.78]]},
            "legs":[{"distance":1500.5,"duration":1000.1}]}]}
            """;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/routed-foot/route/v1/foot/", exchange -> {
            calls.incrementAndGet();
            requestPath.set(exchange.getRequestURI().toString());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        service = new WalkingDirectionsService("http://127.0.0.1:" + server.getAddress().getPort() + "/routed-foot/route/v1/foot", 0);
    }

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    private List<Poi> stops() {
        return List.of(Poi.builder().latitude(new BigDecimal("10.77")).longitude(new BigDecimal("106.68")).build(),
                Poi.builder().latitude(new BigDecimal("10.78")).longitude(new BigDecimal("106.69")).build());
    }

    @Test
    void usesFootEndpointInRequestedOrderAndCachesSuccessfulCalculation() {
        var route = service.calculate(stops());
        assertEquals(1501L, route.distanceMeters());
        assertEquals(1001, route.durationSeconds());
        assertEquals("LineString", route.geometry().get("type"));
        assertTrue(requestPath.get().startsWith("/routed-foot/route/v1/foot/106.68,10.77;106.69,10.78?"));
        assertSame(route, service.calculate(stops()));
        assertEquals(1, calls.get());
    }

    @Test
    void reportsNoRouteWithoutFabricatingGeometryOrDistance() {
        status = 400;
        body = "{\"code\":\"NoRoute\"}";
        assertThrows(WalkingDirectionsService.NoWalkingRouteException.class, () -> service.calculate(stops()));
    }

    @Test
    void handlesUnavailableAndMalformedProviderResponses() {
        status = 503;
        body = "Service unavailable";
        assertThrows(IllegalStateException.class, () -> service.calculate(stops()));
        status = 200;
        body = "{\"code\":\"Ok\",\"routes\":[{\"distance\":10,\"duration\":5}]}";
        assertThrows(IllegalStateException.class, () -> service.calculate(stops()));
        body = "null";
        assertThrows(IllegalStateException.class, () -> service.calculate(stops()));
    }
}
