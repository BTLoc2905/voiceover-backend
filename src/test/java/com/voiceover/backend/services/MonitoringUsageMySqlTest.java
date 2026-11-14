package com.voiceover.backend.services;

import com.voiceover.backend.repositories.UsageEventRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in; every write and cleanup is confined to a randomly named test database. */
@EnabledIfSystemProperty(named = "monitoring.mysql.integration", matches = "true")
class MonitoringUsageMySqlTest {
    static final String DATABASE = "voicemap_monitoring_test_" + UUID.randomUUID().toString().replace("-", "");
    static String url, password;
    static boolean created;
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static UsageEventRepository usage;

    @BeforeAll static void setup() throws Exception {
        url = System.getenv("MONITORING_TEST_MYSQL_URL");
        assertNotNull(url, "Explicit isolated MONITORING_TEST_MYSQL_URL is required.");
        password = System.getenv().getOrDefault("MONITORING_TEST_DB_PASSWORD", "");
        try (var connection = DriverManager.getConnection(url, "root", password); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + DATABASE + " CHARACTER SET utf8mb4"); created = true;
        }
        int slash = url.indexOf('/', "jdbc:mysql://".length()), query = url.indexOf('?');
        DataSource source = new DriverManagerDataSource(url.substring(0, slash + 1) + DATABASE + (query < 0 ? "" : url.substring(query)), "root", password);
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> source);
        context.register(ContentPipelineMySqlTest.Config.class); context.refresh();
        usage = context.getBean(UsageEventRepository.class);
    }

    @AfterAll static void cleanup() throws Exception {
        if (context != null) context.close();
        if (created && DATABASE.matches("voicemap_monitoring_test_[a-f0-9]{32}")) {
            try (var connection = DriverManager.getConnection(url, "root", password); var statement = connection.createStatement()) {
                statement.execute("DROP DATABASE " + DATABASE);
            }
        }
    }

    @BeforeEach void clearTestEvents() { jdbc.update("DELETE FROM usage_event"); }

    void event(String client, String type, Long poi, Long route, String language, LocalDateTime at) {
        jdbc.update("INSERT INTO usage_event (anon_client_id, playback_id, event_type, poi_id, route_id, language_code, occurred_at) VALUES (?,?,?,?,?,?,?)",
                client, UUID.randomUUID().toString(), type, poi, route, language, Timestamp.valueOf(at));
    }

    @Test void activeWindowCountsDistinctNonEmptyIdentifiersAndIncludesExactTwoMinuteBoundary() {
        var now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS); var since = now.minusMinutes(2);
        event("A", "APP_OPEN", null, null, null, now);
        event("A", "APP_ACTIVE", null, null, null, now);
        event("B", "APP_ACTIVE", null, null, null, since);
        event("C", "APP_ACTIVE", null, null, null, since.minus(1, ChronoUnit.MILLIS));
        event(null, "APP_OPEN", null, null, null, now); event("", "APP_OPEN", null, null, null, now);
        assertEquals(2, usage.countActiveUsers(since));
        assertEquals(1, usage.countActiveUsers(since.plus(1, ChronoUnit.MILLIS)));
    }

    @Test void statisticsExcludeMetadataCompletionAndOldEventsAndCountOnlyStartedRoutes() {
        var now = LocalDateTime.now(); var since = now.minusHours(24);
        event("A", "AUTO_PLAY", 1L, null, "vi", now); event("B", "TAP_POI", 1L, null, "en", now);
        event("A", "AUTO_PLAY", 2L, null, "vi", now); event("A", "AUDIO_COMPLETED", 2L, null, "vi", now);
        event("C", "AUTO_PLAY", 1L, null, "vi", now.minusHours(25));
        event("A", "APP_OPEN", null, 7L, null, now);
        event("A", "ROUTE_SELECTED", null, 7L, null, now); event("B", "ROUTE_SELECTED", null, 7L, null, now);
        event("C", "ROUTE_SELECTED", null, 8L, null, now); event("A", "ROUTE_SELECTED", null, 7L, null, now.minusHours(25));
        assertEquals(Map.of(1L, 2L, 2L, 1L), counts(usage.countRecentListensByPoi(since)));
        assertEquals(Map.of("vi", 2L, "en", 1L), counts(usage.countRecentListensByLanguage(since)));
        assertEquals(Map.of("AUTO_PLAY", 2L, "TAP_POI", 1L), counts(usage.countRecentListensByTrigger(since)));
        assertEquals(Map.of(7L, 2L, 8L, 1L), counts(usage.countRecentRouteSelections(since)));
    }

    Map<Object, Long> counts(List<Object[]> rows) {
        var result = new HashMap<Object, Long>();
        for (var row : rows) result.put(row[0], ((Number) row[1]).longValue());
        return result;
    }
}
