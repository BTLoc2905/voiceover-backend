package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in: creates/drops only a random voicemap_pipeline_test_* database, never the application's DB. */
@EnabledIfSystemProperty(named = "pipeline.mysql.integration", matches = "true")
class ContentPipelineMySqlTest {
    static final String DATABASE = "voicemap_pipeline_test_" + UUID.randomUUID().toString().replace("-", "");
    static String bootstrapUrl, user, password;
    static DataSource dataSource;
    static boolean created;
    static AnnotationConfigApplicationContext context;
    static long legacyPoiId;

    @BeforeAll static void setup() throws Exception {
        bootstrapUrl = System.getProperty("pipeline.test.url", env("PIPELINE_TEST_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectTimeout=3000"));
        user = env("PIPELINE_TEST_DB_USER", env("SPRING_DATASOURCE_USERNAME", "root"));
        password = System.getProperty("pipeline.test.password", env("PIPELINE_TEST_DB_PASSWORD", env("SPRING_DATASOURCE_PASSWORD", "root")));
        try (Connection connection = DriverManager.getConnection(bootstrapUrl, user, password); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + DATABASE + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); created = true;
        }
        int slash = bootstrapUrl.indexOf('/', "jdbc:mysql://".length());
        int query = bootstrapUrl.indexOf('?');
        String url = bootstrapUrl.substring(0, slash + 1) + DATABASE + (query < 0 ? "" : bootstrapUrl.substring(query));
        dataSource = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(dataSource).target("13").load().migrate();
        insertLegacyContent();
        Flyway.configure().dataSource(dataSource).load().migrate();
        openContext();
    }

    static void openContext() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.register(Config.class); context.refresh();
    }

    @AfterAll static void cleanup() throws Exception {
        if (context != null) context.close();
        if (created && DATABASE.matches("voicemap_pipeline_test_[a-f0-9]{32}")) {
            try (Connection connection = DriverManager.getConnection(bootstrapUrl, user, password); Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE " + DATABASE);
            }
        }
    }

    @Test void migrationsAtomicQueueAndRecoveryUseRealRowsAcrossContextRestart() {
        Poi poi = context.getBean(PoiService.class).saveOrUpdatePoi(newPoi());
        long poiId = poi.getId();
        var tasks = context.getBean(PipelineTaskRepository.class).findByPoiId(poiId);
        assertEquals(16, tasks.size()); assertTrue(tasks.stream().allMatch(t -> "PENDING".equals(t.getStatus())));
        assertEquals(16, context.getBean(CommittedEvents.class).ids.stream().filter(id -> tasks.stream().anyMatch(t -> t.getId().equals(id))).count());
        long taskId = tasks.stream().filter(t -> "en".equals(t.getTargetLanguageCode())).findFirst().orElseThrow().getId();
        var jobs = context.getBean(TranslationReviewService.class);
        var original = jobs.claim(taskId);
        var translated = jobs.saveTranslation(original, "Persistent English");
        transaction().executeWithoutResult(status -> {
            var repo = context.getBean(PipelineTaskRepository.class); var task = repo.findById(taskId).orElseThrow();
            task.setLeaseUntil(LocalDateTime.now().minusSeconds(5)); repo.saveAndFlush(task);
        });
        context.close(); openContext();
        jobs = context.getBean(TranslationReviewService.class);
        assertTrue(jobs.dispatchableIds().contains(taskId)); var recovered = jobs.claim(taskId);
        assertNotEquals(original.claimToken(), recovered.claimToken()); assertEquals("Persistent English", recovered.text());
        assertFalse(jobs.complete(translated, new AudioGenerationService.GeneratedAudio("/old.wav", 1)));
        assertTrue(jobs.complete(recovered, new AudioGenerationService.GeneratedAudio("/new.wav", 20)));
        var audio = context.getBean(AudioRepository.class).findByPoiIdAndLanguageCode(poiId, "en").orElseThrow();
        assertEquals("COMPLETED", audio.getStatus()); assertEquals("/new.wav", audio.getFilePath()); assertEquals(20, audio.getDurationSec());
    }

    @Test void rollbackLeavesNeitherSourceNorTasksNorCommitEvents() {
        var pois = context.getBean(PoiRepository.class); var tasks = context.getBean(PipelineTaskRepository.class);
        long beforePois = pois.count(), beforeTasks = tasks.count(); int beforeEvents = context.getBean(CommittedEvents.class).ids.size();
        transaction().executeWithoutResult(status -> {
            context.getBean(PoiService.class).saveOrUpdatePoi(newPoi()); status.setRollbackOnly();
        });
        assertEquals(beforePois, pois.count()); assertEquals(beforeTasks, tasks.count());
        assertEquals(beforeEvents, context.getBean(CommittedEvents.class).ids.size());
    }

    @Test void upgradeKeepsLegacyTextAndMarksManualContentStaleOnlyOnExplicitSourceSave() {
        var pois = context.getBean(PoiRepository.class); var texts = context.getBean(TranslationRepository.class);
        var tasks = context.getBean(PipelineTaskRepository.class); var audios = context.getBean(AudioRepository.class);
        Poi legacy = pois.findById(legacyPoiId).orElseThrow();
        assertEquals(0, legacy.getContentRevision()); assertNull(legacy.getSourceFingerprint());
        assertEquals("Legacy English", texts.findByPoiIdAndLanguageCode(legacyPoiId, "en").orElseThrow().getContent());
        assertEquals("/legacy.wav", audios.findByPoiIdAndLanguageCode(legacyPoiId, "en").orElseThrow().getFilePath());
        assertTrue(tasks.findByPoiId(legacyPoiId).stream().allMatch(t -> "FAILED".equals(t.getStatus())));
        context.getBean(PoiService.class).saveOrUpdatePoi(legacy);
        var text = texts.findByPoiIdAndLanguageCode(legacyPoiId, "en").orElseThrow();
        assertEquals("STALE", text.getStatus()); assertEquals("Legacy English", text.getContent());
        assertNull(audios.findByPoiIdAndLanguageCode(legacyPoiId, "en").orElseThrow().getFilePath());
        var jobs = context.getBean(TranslationReviewService.class);
        jobs.submit(legacyPoiId, "en", text.getContent(), TranslationReviewService.revision(text), false);
        long id = tasks.findFirstByPoiIdAndTargetLanguageCodeOrderByIdDesc(legacyPoiId, "en").orElseThrow().getId();
        assertTrue(jobs.complete(jobs.claim(id), new AudioGenerationService.GeneratedAudio("/confirmed.wav", 9)));
        assertEquals("/confirmed.wav", audios.findByPoiIdAndLanguageCode(legacyPoiId, "en").orElseThrow().getFilePath());
    }

    @Test void rollbackOfANewerSourceRestoresThePreviousClaimAndPendingLanguages() {
        Poi poi = context.getBean(PoiService.class).saveOrUpdatePoi(newPoi());
        var tasks = context.getBean(PipelineTaskRepository.class); var jobs = context.getBean(TranslationReviewService.class);
        long id = tasks.findFirstByPoiIdAndTargetLanguageCodeOrderByIdDesc(poi.getId(), "en").orElseThrow().getId();
        var original = jobs.claim(id);
        transaction().executeWithoutResult(status -> {
            poi.setDescriptionSource("Rolled back B"); context.getBean(PoiService.class).saveOrUpdatePoi(poi); status.setRollbackOnly();
        });
        assertEquals(1, context.getBean(PoiRepository.class).findById(poi.getId()).orElseThrow().getContentRevision());
        assertEquals(16, tasks.findByPoiId(poi.getId()).size());
        assertEquals("PROCESSING", tasks.findById(id).orElseThrow().getStatus());
        assertNotNull(jobs.saveTranslation(original, "Original A is still current"));
    }

    @Test void concurrentClaimsHaveOneOwnerAndOldSourceCannotPublish() throws Exception {
        Poi poi = context.getBean(PoiService.class).saveOrUpdatePoi(newPoi());
        var tasks = context.getBean(PipelineTaskRepository.class);
        long id = tasks.findByPoiId(poi.getId()).stream().filter(t -> "en".equals(t.getTargetLanguageCode())).findFirst().orElseThrow().getId();
        var jobs = context.getBean(TranslationReviewService.class);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<TranslationReviewService.Work> claim = () -> { start.await(); return jobs.claim(id); };
            var first = executor.submit(claim); var second = executor.submit(claim); start.countDown();
            var a = first.get(10, TimeUnit.SECONDS); var b = second.get(10, TimeUnit.SECONDS);
            assertTrue((a == null) != (b == null));
            var winner = a == null ? b : a;
            poi.setDescriptionSource("New version B");
            Poi saved = context.getBean(PoiService.class).saveOrUpdatePoi(poi);
            assertEquals(2, saved.getContentRevision()); assertNull(jobs.saveTranslation(winner, "Late A"));
            var text = context.getBean(TranslationRepository.class).findByPoiIdAndLanguageCode(poi.getId(), "en").orElseThrow();
            assertEquals("PENDING", text.getStatus()); assertNull(text.getContent());
            assertEquals(32, tasks.findByPoiId(poi.getId()).size());
        }
    }

    private static Poi newPoi() {
        return Poi.builder().name("Integration " + UUID.randomUUID()).descriptionSource("Description A").sourceLanguageCode("vi")
                .latitude(BigDecimal.TEN).longitude(BigDecimal.TEN).status("DRAFT").build();
    }
    private static String env(String name, String fallback) { String value = System.getenv(name); return value == null ? fallback : value; }
    private static TransactionTemplate transaction() { return new TransactionTemplate(context.getBean(PlatformTransactionManager.class)); }

    private static void insertLegacyContent() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO poi(name,latitude,longitude,description_source,source_language_code,status) VALUES ('Legacy upgrade fixture',10,10,'Legacy Source','vi','PUBLISHED')", Statement.RETURN_GENERATED_KEYS);
            try (ResultSet keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); legacyPoiId = keys.getLong(1); }
            statement.executeUpdate("INSERT INTO translation(poi_id,language_code,content,status,source_hash,is_manual_edit) VALUES (" + legacyPoiId + ",'en','Legacy English','COMPLETED',SHA2('Legacy Source',256),TRUE)", Statement.RETURN_GENERATED_KEYS);
            long translationId;
            try (ResultSet keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); translationId = keys.getLong(1); }
            statement.executeUpdate("INSERT INTO audio(translation_id,poi_id,language_code,file_path,durtation_rec,status) VALUES (" + translationId + "," + legacyPoiId + ",'en','/legacy.wav',12,'COMPLETED')");
            statement.executeUpdate("INSERT INTO pipeline_task(poi_id,target_language_code,task_type,status) VALUES (" + legacyPoiId + ",'en','TRANSLATE_TTS','PROCESSING'),(" + legacyPoiId + ",'en','TTS_ONLY','PENDING')");
        }
    }

    static class CommittedEvents {
        final List<Long> ids = new CopyOnWriteArrayList<>();
        @TransactionalEventListener public void committed(ReviewAudioRequested event) { ids.add(event.taskId()); }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.voiceover.backend.repositories")
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source);
            factory.setPackagesToScan("com.voiceover.backend.entities"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none")); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean AudioGenerationService generator() {
            AudioGenerationService generator = mock(AudioGenerationService.class);
            when(generator.supportsLanguage(anyString())).thenReturn(true);
            return generator;
        }
        @Bean CommittedEvents committedEvents() { return new CommittedEvents(); }
        @Bean TranslationReviewService jobs(PoiRepository pois, LanguageRepository languages, TranslationRepository translations,
                AudioRepository audios, PipelineTaskRepository tasks, ApplicationEventPublisher events, AudioGenerationService generator,
                ProtectedTermRepository terms, SystemLogRepository logs) {
            return new TranslationReviewService(pois, languages, translations, audios, tasks, events, generator, terms, logs);
        }
        @Bean PoiService poiService(PoiRepository pois, LanguageRepository languages, TranslationRepository translations,
                AudioRepository audios, RouteStopRepository stops, TranslationReviewService jobs) {
            return new PoiService(pois, translations, audios, languages, stops, jobs, mock(PoiThumbnailService.class));
        }
    }
}
