package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TranslationReviewServiceTest {
    final PoiRepository pois = mock(PoiRepository.class);
    final LanguageRepository languages = mock(LanguageRepository.class);
    final TranslationRepository texts = mock(TranslationRepository.class);
    final AudioRepository audios = mock(AudioRepository.class);
    final PipelineTaskRepository tasks = mock(PipelineTaskRepository.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final AudioGenerationService generator = mock(AudioGenerationService.class);
    final TranslationReviewService service = new TranslationReviewService(pois, languages, texts, audios, tasks, events, generator);
    final List<PipelineTask> jobs = new ArrayList<>();
    final Translation vi = Translation.builder().id(20L).poiId(1L).languageCode("vi").content("Bản cũ").status("COMPLETED").build();
    final Translation en = Translation.builder().id(21L).poiId(1L).languageCode("en").content("English unchanged").status("COMPLETED").build();
    final Audio audio = Audio.builder().id(30L).translationId(20L).poiId(1L).languageCode("vi").status("COMPLETED").filePath("/audio/old.mp3").build();
    @BeforeEach void setup() {
        Poi poi = Poi.builder().id(1L).name("POI").descriptionSource("Nguồn").sourceLanguageCode("vi").status("PUBLISHED").latitude(BigDecimal.TEN).longitude(BigDecimal.TEN).build();
        when(pois.findById(1L)).thenReturn(Optional.of(poi)); when(pois.findForUpdate(1L)).thenReturn(Optional.of(poi));
        Language lang = Language.builder().code("vi").name("Tiếng Việt").isActive(true).ttsSupported(true).build();
        when(languages.findById("vi")).thenReturn(Optional.of(lang)); when(languages.findAll()).thenReturn(List.of(lang));
        when(generator.provider(anyString())).thenReturn("UNAVAILABLE");
        when(texts.findByPoiIdAndLanguageCode(1L, "vi")).thenReturn(Optional.of(vi));
        when(texts.findByPoiId(1L)).thenReturn(List.of(vi, en)); when(audios.findByPoiId(1L)).thenReturn(List.of(audio));
        when(audios.findByPoiIdAndLanguageCode(1L, "vi")).thenReturn(Optional.of(audio));
        when(texts.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0)); when(audios.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
        AtomicLong ids = new AtomicLong(100);
        when(tasks.saveAndFlush(any())).thenAnswer(a -> { PipelineTask job = a.getArgument(0); if (job.getId() == null) { job.setId(ids.incrementAndGet()); jobs.add(job); } return job; });
        when(tasks.findByPoiId(1L)).thenAnswer(a -> new ArrayList<>(jobs));
        when(tasks.findById(anyLong())).thenAnswer(a -> jobs.stream().filter(t -> t.getId().equals(a.getArgument(0))).findFirst());
        when(tasks.findPoiIdById(anyLong())).thenAnswer(a -> jobs.stream().filter(t -> t.getId().equals(a.getArgument(0))).map(PipelineTask::getPoiId).findFirst());
        when(tasks.findFirstByPoiIdAndTargetLanguageCodeOrderByIdDesc(1L,"vi")).thenAnswer(a -> jobs.stream().filter(t -> "vi".equals(t.getTargetLanguageCode())).max(Comparator.comparing(PipelineTask::getId)));
        when(tasks.existsByPoiIdAndTargetLanguageCodeAndStatusIn(eq(1L),eq("vi"),anyCollection())).thenAnswer(a -> jobs.stream().anyMatch(t -> "vi".equals(t.getTargetLanguageCode()) && List.of("PENDING","PROCESSING").contains(t.getStatus())));
    }
    String revision() { return TranslationReviewService.revision(vi); }

    @Test void savesOnlySelectedLanguageAndQueuesAfterPersistenceWithoutPretendingAudioExists() throws Exception {
        var result = service.submit(1L,"vi","Bản đã sửa đầy đủ",revision(),false);
        assertEquals("Bản đã sửa đầy đủ",vi.getContent()); assertTrue(vi.isManualEdit());
        assertEquals("English unchanged",en.getContent());
        assertEquals("PENDING",result.review().task().status()); assertEquals("TTS_ONLY",result.review().task().type());
        assertEquals("PENDING",audio.getStatus()); assertNull(audio.getFilePath());
        verify(texts).saveAndFlush(vi); verify(texts,never()).saveAndFlush(en);
        verify(events).publishEvent(new ReviewAudioRequested(jobs.getFirst().getId()));
        verify(generator,never()).generate(any(),any(),any());
    }
    @Test void readDoesNotSaveGenerateOrEnqueue() {
        var result = service.index(1L); assertEquals(1,result.languages().size());
        verify(texts,never()).saveAndFlush(any()); verify(audios,never()).saveAndFlush(any()); verify(tasks,never()).saveAndFlush(any()); verifyNoInteractions(events);
    }
    @Test void rejectsBusyOrStaleWriteAndKeepsStoredText() {
        jobs.add(PipelineTask.builder().id(5L).poiId(1L).targetLanguageCode("vi").taskType("TRANSLATE_TTS").status("PROCESSING").build());
        assertThrows(ContentReviewConflictException.class,()->service.submit(1L,"vi","New",revision(),false));
        jobs.clear(); assertThrows(ContentReviewConflictException.class,()->service.submit(1L,"vi","New","old-revision",false));
        assertEquals("Bản cũ",vi.getContent()); verify(texts,never()).saveAndFlush(any()); verifyNoInteractions(events);
    }
    @Test void blankOversizeMissingAndInactiveInputsDoNotQueue() {
        assertThrows(IllegalArgumentException.class,()->service.submit(1L,"vi","  ",revision(),false));
        assertThrows(IllegalArgumentException.class,()->service.submit(1L,"vi","ạ".repeat(21000),revision(),false));
        when(texts.findByPoiIdAndLanguageCode(1L,"vi")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,()->service.submit(1L,"vi","New",revision(),false));
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(false).build()));
        assertThrows(IllegalArgumentException.class,()->service.submit(1L,"vi","New",revision(),false)); verifyNoInteractions(events);
    }
    @Test void regenerationUsesStoredTextAndTransitionsToARealVersionedFile() {
        service.submit(1L,"vi",null,revision(),true); verify(texts,never()).saveAndFlush(any());
        var work = service.begin(jobs.getFirst().getId()); assertEquals("Bản cũ",work.text()); assertEquals("PROCESSING",audio.getStatus());
        service.complete(work,new AudioGenerationService.GeneratedAudio("/storage/audio/new-uuid.wav",32),1);
        assertEquals("COMPLETED",audio.getStatus()); assertEquals("/storage/audio/new-uuid.wav",audio.getFilePath());
        assertEquals(32,audio.getDurationSec()); assertEquals("COMPLETED",jobs.getFirst().getStatus()); assertEquals(1,jobs.getFirst().getRetryCount());
    }
    @Test void failedAudioPreservesSavedTranslationAndAllowsRetry() {
        service.submit(1L,"vi","Bản đã lưu",revision(),false); long id=jobs.getFirst().getId();
        service.begin(id); service.fail(id,"TTS unavailable",3);
        assertEquals("Bản đã lưu",vi.getContent()); assertEquals("COMPLETED",vi.getStatus()); assertEquals("FAILED",audio.getStatus()); assertNull(audio.getFilePath());
        service.submit(1L,"vi",null,revision(),true); assertEquals(2,jobs.size()); assertEquals("PENDING",jobs.getLast().getStatus());
    }
    @Test void staleWorkerCannotPublishAudioForDifferentText() {
        service.submit(1L,"vi",null,revision(),true); var work=service.begin(jobs.getFirst().getId());
        vi.setContent("Changed elsewhere"); service.complete(work,new AudioGenerationService.GeneratedAudio("/stale.wav",10),0);
        assertEquals("FAILED",jobs.getFirst().getStatus()); assertEquals("FAILED",audio.getStatus()); assertNull(audio.getFilePath());
    }
    @Test void textOnlyCompletesWithoutAFileAndRevisionIgnoresDatabaseTimestampPrecision() {
        String before=revision(); vi.setUpdatedAt(LocalDateTime.now()); assertEquals(before,revision());
        service.submit(1L,"vi",null,revision(),true); var work=service.begin(jobs.getFirst().getId()); service.complete(work,null,0);
        assertEquals("TEXT_ONLY",audio.getStatus()); assertNull(audio.getFilePath()); assertEquals("COMPLETED",jobs.getFirst().getStatus());
    }
    @Test void fullPipelineDoesNotOverwriteAnActiveManualJob() {
        service.submit(1L,"vi","Manual",revision(),false);
        assertNull(service.reserveTranslation(1L,"vi",vi.getSourceHash())); assertEquals(1,jobs.size());
        service.fail(jobs.getFirst().getId(),"TTS failure",3);
        assertNull(service.reserveTranslation(1L,"vi",vi.getSourceHash()));
    }

    @Test void completedTranslationWithFailedAudioQueuesOnlyTts() throws Exception {
        vi.setSourceHash(hash("Nguồn"));
        vi.setStatus("COMPLETED");
        vi.setManualEdit(true);
        audio.setStatus("FAILED");
        when(generator.provider("vi")).thenReturn("Google Cloud TTS");
        PipelineTask task = service.reserveTranslation(1L,"vi",hash("Nguồn"));
        assertEquals("TTS_ONLY",task.getTaskType());
        assertTrue(vi.isManualEdit());
    }

    @Test void completedTranslationIsSkippedOnlyWhenTheAudioFileExists() throws Exception {
        vi.setSourceHash(hash("Nguồn"));
        vi.setStatus("COMPLETED");
        audio.setStatus("COMPLETED");
        when(generator.provider("vi")).thenReturn("Google Cloud TTS");
        when(generator.hasUsableFile("/audio/old.mp3")).thenReturn(true);
        assertNull(service.reserveTranslation(1L,"vi",hash("Nguồn")));
    }

    @Test void missingCompletedAudioIsNotReportedReadyWhenProviderIsUnavailable() throws Exception {
        vi.setSourceHash(hash("Nguồn"));
        vi.setStatus("COMPLETED");
        audio.setStatus("COMPLETED");
        audio.setFilePath("/audio/file-that-does-not-exist.wav");
        when(generator.provider("vi")).thenReturn("UNAVAILABLE");
        when(generator.hasUsableFile(audio.getFilePath())).thenReturn(false);
        PipelineTask task = service.reserveTranslation(1L,"vi",hash("Nguồn"));
        assertEquals("TTS_ONLY",task.getTaskType());
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
