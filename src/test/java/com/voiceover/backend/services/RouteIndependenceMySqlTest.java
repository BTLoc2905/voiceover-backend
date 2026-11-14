package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** All writes, migrations and cleanup use a random database on an explicitly supplied test server. */
@EnabledIfSystemProperty(named = "poi.mysql.integration", matches = "true")
class RouteIndependenceMySqlTest {
    String database, bootstrapUrl, user, password;
    boolean created;
    DataSource source;
    JdbcTemplate jdbc;
    AnnotationConfigApplicationContext context;
    RouteService service;
    List<Map<String, Object>> originalStops;

    @BeforeEach void setup() throws Exception {
        bootstrapUrl = System.getenv("POI_TEST_MYSQL_URL");
        assertNotNull(bootstrapUrl, "An isolated POI_TEST_MYSQL_URL is required.");
        user = System.getenv().getOrDefault("POI_TEST_DB_USER", "root");
        password = System.getenv().getOrDefault("POI_TEST_DB_PASSWORD", "");
        database = "voicemap_route_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(bootstrapUrl, user, password); Statement sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            created = true;
        }
        int slash = bootstrapUrl.indexOf('/', "jdbc:mysql://".length());
        int query = bootstrapUrl.indexOf('?');
        source = new DriverManagerDataSource(bootstrapUrl.substring(0, slash + 1) + database
                + (query < 0 ? "" : bootstrapUrl.substring(query)), user, password);
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("19").load().migrate();
        for (long id = 11; id <= 24; id++) jdbc.update("INSERT INTO poi (poi_id, name, latitude, longitude, description_source, source_language_code, status) VALUES (?, ?, 10.77, 106.68, 'Keep description', 'vi', ?)",
                id, "POI " + id, id == 23 ? "DRAFT" : id == 24 ? "HIDDEN" : "PUBLISHED");
        jdbc.update("INSERT INTO session (session_id, name, description, status) VALUES (72, 'Legacy session', 'Keep session data', 'Draft')");
        jdbc.update("INSERT INTO route (route_id, session_id, name, status) VALUES (91, 72, 'Legacy published route', 'PUBLISHED'), (92, 72, 'Legacy closed route', 'CLOSED')");
        jdbc.update("INSERT INTO route_stop (routestop_id, route_id, poi_id, stop_order) VALUES (801, 91, 12, 1), (802, 91, 11, 2)");
        originalStops = jdbc.queryForList("SELECT * FROM route_stop ORDER BY routestop_id");
        assertEquals(1, Flyway.configure().dataSource(source).load().migrate().migrationsExecuted);
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source);
        context.register(PoiHiddenMySqlTest.PoiConfig.class, RouteConfig.class);
        context.refresh();
        service = context.getBean(RouteService.class);
    }

    @AfterEach void cleanup() throws Exception {
        if (context != null) context.close();
        if (created && database.matches("voicemap_route_test_[a-f0-9]{32}")) {
            try (Connection connection = DriverManager.getConnection(bootstrapUrl, user, password); Statement sql = connection.createStatement()) {
                sql.execute("DROP DATABASE " + database);
            }
        }
    }

    @Test void v20PreservesLegacyRowsIdsAndStopOrderWithoutSessionGate() {
        assertEquals(originalStops, jdbc.queryForList("SELECT * FROM route_stop ORDER BY routestop_id"));
        assertEquals(List.of(91L, 92L), jdbc.queryForList("SELECT route_id FROM route ORDER BY route_id", Long.class));
        assertEquals(72L, jdbc.queryForObject("SELECT session_id FROM route WHERE route_id = 91", Long.class));
        assertEquals("Keep session data", jdbc.queryForObject("SELECT description FROM session WHERE session_id = 72", String.class));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM route WHERE route_id = 92", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'route' AND REFERENCED_TABLE_NAME = 'session'", Integer.class));
        assertEquals(List.of(91L), service.getSuggestedRoutes().stream().map(row -> (Long) row.get("id")).toList());
        assertFalse(service.getSuggestedRoutes().getFirst().containsKey("sessionId"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE route SET status = 'CLOSED' WHERE route_id = 92"));
    }

    @Test void independentDraftPublishEditHideDeleteAndPoiGuardsUseRealDatabase() {
        Route route = service.createRoute("Independent", "Draft description", null, List.of());
        Long id = route.getId();
        assertEquals("DRAFT", route.getStatus());
        assertNull(jdbc.queryForObject("SELECT session_id FROM route WHERE route_id = ?", Long.class, id));
        service.updateRoute(id, "Independent", "One POI", "DRAFT", List.of(13L));
        assertEquals(List.of(13L), service.getRoute(id).get("poiIds"));
        route = service.updateRoute(id, "Independent", "Published description", "PUBLISHED", List.of(13L, 14L));
        assertEquals("PUBLISHED", route.getStatus());
        service.updateRoute(id, "Independent", "Reordered", "PUBLISHED", List.of(14L, 13L));
        assertEquals(List.of(14L, 13L), service.getRoute(id).get("poiIds"));
        PoiService poiService = context.getBean(PoiService.class);
        Poi hidden = context.getBean(PoiRepository.class).findById(13L).orElseThrow();
        hidden.setStatus("HIDDEN");
        assertTrue(assertThrows(RouteConflictException.class, () -> poiService.saveOrUpdatePoi(hidden)).getMessage().contains("Independent"));
        assertThrows(RouteConflictException.class, () -> poiService.deletePoiSafely(13L));
        service.updateRoute(id, "Independent", "Reordered", "DRAFT", List.of(14L, 13L));
        assertEquals("DRAFT", service.getRoute(id).get("status"));
        assertThrows(RouteConflictException.class, () -> poiService.deletePoiSafely(13L));
        assertEquals("HIDDEN", poiService.saveOrUpdatePoi(hidden).getStatus());
        long poiCount = jdbc.queryForObject("SELECT COUNT(*) FROM poi", Long.class);
        service.deleteRoute(id);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM route_stop WHERE route_id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM route WHERE route_id = ?", Integer.class, id));
        assertEquals(poiCount, jdbc.queryForObject("SELECT COUNT(*) FROM poi", Long.class));
    }

    @Test void publicationRevalidatesAndAnonymousPersonalRouteNeverWritesOriginal() {
        for (List<Long> invalid : List.of(List.of(13L), LongStream.rangeClosed(11, 21).boxed().toList(), List.of(13L, 13L), List.of(13L, 23L), List.of(13L, 24L))) {
            assertThrows(IllegalArgumentException.class, () -> service.createRoute("Invalid", "", "PUBLISHED", invalid));
        }
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM route", Long.class));
        Route ten = service.createRoute("Ten POIs", "", "PUBLISHED", LongStream.rangeClosed(11, 20).boxed().toList());
        assertEquals(10, service.getRoute(ten.getId()).get("stopCount"));
        assertThrows(IllegalArgumentException.class, () -> service.updateRoute(ten.getId(), "Invalid edit", "", "PUBLISHED", List.of(13L, 24L)));
        assertEquals(10, service.getRoute(ten.getId()).get("stopCount"));
        jdbc.update("INSERT INTO route (name, status) VALUES ('Broken published route', 'PUBLISHED')");
        assertEquals(2, service.getSuggestedRoutes().size()); // Legacy valid route and ten-POI route only.
        Map<String, Object> start = service.startRoute(91L, List.of(14L, 13L));
        assertEquals(900L, start.get("totalDistanceMeters"));
        assertEquals(11, start.get("estimatedDurationMinutes"));
        assertEquals(List.of(12L, 11L), service.getRoute(91L).get("poiIds"));
        assertEquals(originalStops, jdbc.queryForList("SELECT * FROM route_stop WHERE route_id = 91 ORDER BY routestop_id"));
    }

    @Configuration(proxyBeanMethods = false)
    static class RouteConfig {
        @Bean WalkingDirectionsService directions() {
            WalkingDirectionsService directions = mock(WalkingDirectionsService.class);
            when(directions.calculate(anyList())).thenReturn(new WalkingDirectionsService.WalkingRoute(900, 650,
                    Map.of("type", "LineString", "coordinates", List.of(List.of(106.68, 10.77), List.of(106.69, 10.78))), List.of()));
            return directions;
        }
        @Bean RouteService routeService(RouteRepository routes, RouteStopRepository stops, PoiRepository pois,
                WalkingDirectionsService directions, AudioRepository audios, TranslationRepository translations, LanguageRepository languages) {
            return new RouteService(routes, stops, pois, directions, audios, translations, languages);
        }
    }
}
