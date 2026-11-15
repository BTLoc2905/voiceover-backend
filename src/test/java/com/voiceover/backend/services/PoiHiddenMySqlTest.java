package com.voiceover.backend.services;

import com.voiceover.backend.entities.Poi;
import com.voiceover.backend.repositories.*;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Opt-in: all writes and cleanup are confined to a new, randomly named test database. */
@EnabledIfSystemProperty(named = "poi.mysql.integration", matches = "true")
class PoiHiddenMySqlTest {
    private String database, bootstrapUrl, user, password;
    private boolean created;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private AnnotationConfigApplicationContext context;

    @BeforeEach void createTestDatabase() throws Exception {
        bootstrapUrl = System.getenv("POI_TEST_MYSQL_URL");
        assertNotNull(bootstrapUrl, "Set POI_TEST_MYSQL_URL to an isolated MySQL test server.");
        user = System.getenv().getOrDefault("POI_TEST_DB_USER", "root");
        password = System.getenv().getOrDefault("POI_TEST_DB_PASSWORD", "");
        database = "voicemap_poi_hidden_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(bootstrapUrl, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            created = true;
        }
        int slash = bootstrapUrl.indexOf('/', "jdbc:mysql://".length());
        int query = bootstrapUrl.indexOf('?');
        String url = bootstrapUrl.substring(0, slash + 1) + database
                + (query < 0 ? "" : bootstrapUrl.substring(query));
        dataSource = new DriverManagerDataSource(url, user, password);
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("18").load().migrate();
        for (String status : List.of("Draft", "Published", "Archived")) {
            jdbc.update("INSERT INTO poi (name, latitude, longitude, description_source, source_language_code, status) "
                    + "VALUES (?, 0, 0, 'Legacy description', 'vi', ?)", "Legacy " + status, status);
        }
    }

    @AfterEach void removeOnlyTestDatabase() throws Exception {
        if (context != null) context.close();
        if (created && database.matches("voicemap_poi_hidden_test_[a-f0-9]{32}")) {
            try (Connection connection = DriverManager.getConnection(bootstrapUrl, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE " + database);
            }
        }
    }

    @Test void upgradesV18AndEnforcesLifecycleAndPublishedRouteProtection() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            SQLException rejected = assertThrows(SQLException.class,
                    () -> statement.executeUpdate("UPDATE poi SET status = 'HIDDEN' WHERE name = 'Legacy Draft'"));
            assertEquals(3819, rejected.getErrorCode());
        }

        migrateAndCheckPreservedPois();
        Flyway.configure().dataSource(dataSource).load().migrate();
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.register(PoiConfig.class);
        context.refresh();
        PoiService service = context.getBean(PoiService.class);
        Poi poi = service.saveOrUpdatePoi(Poi.builder().name("POI lifecycle test")
                .descriptionSource("Description retained across status changes").sourceLanguageCode("vi")
                .latitude(new BigDecimal("10.77")).longitude(new BigDecimal("106.68")).build());
        Long id = poi.getId();
        assertEquals("DRAFT", storedStatus(id));

        for (String status : List.of("PUBLISHED", "HIDDEN", "PUBLISHED")) {
            poi.setStatus(status);
            poi = service.saveOrUpdatePoi(poi);
            assertEquals(id, poi.getId());
            assertEquals(status, storedStatus(id));
            assertEquals("Description retained across status changes", poi.getDescriptionSource());
            boolean publiclyVisible = service.getNearbyPublishedPois(10.77, 106.68, 500).stream()
                    .anyMatch(row -> ((Poi) row.get("poi")).getId().equals(id));
            assertEquals("PUBLISHED".equals(status), publiclyVisible);
            if ("HIDDEN".equals(status)) assertThrows(IllegalArgumentException.class,
                    () -> service.getPoiAudioGuide(id, "vi"));
        }

        jdbc.update("INSERT INTO session (name, status) VALUES ('POI guard test parent', 'DRAFT')");
        Long sessionId = jdbc.queryForObject("SELECT session_id FROM session WHERE name = 'POI guard test parent'", Long.class);
        jdbc.update("INSERT INTO route (session_id, name, status) VALUES (?, 'POI guard test route', 'PUBLISHED')", sessionId);
        Long routeId = jdbc.queryForObject("SELECT route_id FROM route WHERE name = 'POI guard test route'", Long.class);
        Long otherPoi = jdbc.queryForObject("SELECT poi_id FROM poi WHERE name = 'Legacy Published'", Long.class);
        jdbc.update("INSERT INTO route_stop (route_id, poi_id, stop_order) VALUES (?, ?, 1), (?, ?, 2)",
                routeId, id, routeId, otherPoi);
        Poi hiddenRequest = poi;
        for (String parentStatus : List.of("DRAFT", "PUBLISHED")) {
            jdbc.update("UPDATE session SET status = ? WHERE session_id = ?", parentStatus, sessionId);
            hiddenRequest.setStatus("HIDDEN");
            assertThrows(RouteConflictException.class, () -> service.saveOrUpdatePoi(hiddenRequest));
            assertEquals("PUBLISHED", storedStatus(id));
        }
        jdbc.update("UPDATE route SET status = 'DRAFT' WHERE route_id = ?", routeId);
        assertEquals("HIDDEN", service.saveOrUpdatePoi(hiddenRequest).getStatus());
        assertEquals("HIDDEN", storedStatus(id));
    }

    @Test void upgradesAnAlreadyExpandedDatabaseConstraintWithoutDeletingPois() {
        jdbc.execute("ALTER TABLE poi DROP CHECK chk_poi_status, ADD CONSTRAINT chk_poi_status "
                + "CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN', 'ARCHIVED'))");
        jdbc.update("INSERT INTO poi (name, latitude, longitude, description_source, source_language_code, status) "
                + "VALUES ('Already hidden', 0, 0, 'Keep this POI', 'vi', 'HIDDEN')");
        migrateAndCheckPreservedPois();
        assertEquals("HIDDEN", jdbc.queryForObject("SELECT status FROM poi WHERE name = 'Already hidden'", String.class));
        assertEquals(4L, jdbc.queryForObject("SELECT COUNT(*) FROM poi", Long.class));
    }

    private void migrateAndCheckPreservedPois() {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM poi", Long.class);
        assertEquals(1, Flyway.configure().dataSource(dataSource).target("19").load().migrate().migrationsExecuted);
        assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM poi", Long.class));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM poi WHERE name = 'Legacy Draft'", String.class));
        assertEquals("PUBLISHED", jdbc.queryForObject("SELECT status FROM poi WHERE name = 'Legacy Published'", String.class));
        assertEquals("HIDDEN", jdbc.queryForObject("SELECT status FROM poi WHERE name = 'Legacy Archived'", String.class));
        var rejected = assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE poi SET status = 'ARCHIVED' WHERE name = 'Legacy Draft'"));
        assertEquals(3819, assertInstanceOf(SQLException.class, rejected.getMostSpecificCause()).getErrorCode());
    }

    private String storedStatus(Long id) {
        return jdbc.queryForObject("SELECT status FROM poi WHERE poi_id = ?", String.class, id);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.voiceover.backend.repositories")
    static class PoiConfig {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.voiceover.backend.entities");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean PoiService poiService(PoiRepository pois, TranslationRepository translations,
                AudioRepository audios, LanguageRepository languages, RouteStopRepository stops) {
            return new PoiService(pois, translations, audios, languages, stops,
                    mock(TranslationReviewService.class), mock(PoiThumbnailService.class), mock(AudioGenerationService.class));
        }
    }
}
