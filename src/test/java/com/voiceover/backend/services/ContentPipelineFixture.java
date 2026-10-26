package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Repository fixture for observable state transitions, not a substitute for DB integration. */
class ContentPipelineFixture {
    final PoiRepository pois = mock(PoiRepository.class);
    final LanguageRepository languages = mock(LanguageRepository.class);
    final TranslationRepository texts = mock(TranslationRepository.class);
    final AudioRepository audios = mock(AudioRepository.class);
    final PipelineTaskRepository tasks = mock(PipelineTaskRepository.class);
    final ProtectedTermRepository terms = mock(ProtectedTermRepository.class);
    final SystemLogRepository logs = mock(SystemLogRepository.class);
    final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    final AudioGenerationService generator = mock(AudioGenerationService.class);
    final Poi poi = Poi.builder().id(1L).name("New POI").descriptionSource("Bonjour Saigon").sourceLanguageCode("fr").status("PUBLISHED").build();
    final Language vi = Language.builder().code("vi").name("Vietnamese").isActive(true).ttsSupported(true).build();
    final Language en = Language.builder().code("en").name("English").isActive(true).ttsSupported(true).build();
    final Map<String, Translation> translations = new LinkedHashMap<>();
    final Map<String, Audio> files = new LinkedHashMap<>();
    final List<PipelineTask> jobs = new ArrayList<>();
    final TranslationReviewService service = new TranslationReviewService(pois, languages, texts, audios, tasks, events, generator, terms, logs);

    ContentPipelineFixture() {
        AtomicLong ids = new AtomicLong();
        when(pois.findById(1L)).thenReturn(Optional.of(poi));
        when(pois.findForUpdate(1L)).thenReturn(Optional.of(poi));
        when(pois.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(languages.findById(anyString())).thenAnswer(call -> List.of(vi, en).stream().filter(l -> l.getCode().equals(call.getArgument(0))).findFirst());
        when(languages.findByIsActiveTrue()).thenReturn(List.of(vi, en));
        when(languages.findAll()).thenReturn(List.of(vi, en));
        when(terms.findAll()).thenReturn(List.of());
        when(texts.findByPoiIdAndLanguageCode(eq(1L), anyString())).thenAnswer(call -> Optional.ofNullable(translations.get(call.getArgument(1))));
        when(texts.findByPoiId(1L)).thenAnswer(call -> new ArrayList<>(translations.values()));
        when(texts.saveAndFlush(any())).thenAnswer(call -> {
            Translation text = call.getArgument(0);
            if (text.getId() == null) text.setId(ids.incrementAndGet());
            translations.put(text.getLanguageCode(), text); return text;
        });
        when(audios.findByPoiIdAndLanguageCode(eq(1L), anyString())).thenAnswer(call -> Optional.ofNullable(files.get(call.getArgument(1))));
        when(audios.findByPoiId(1L)).thenAnswer(call -> new ArrayList<>(files.values()));
        when(audios.saveAndFlush(any())).thenAnswer(call -> {
            Audio audio = call.getArgument(0);
            if (audio.getId() == null) audio.setId(ids.incrementAndGet());
            files.put(audio.getLanguageCode(), audio); return audio;
        });
        when(tasks.saveAndFlush(any())).thenAnswer(call -> {
            PipelineTask task = call.getArgument(0);
            if (task.getId() == null) { task.setId(ids.incrementAndGet()); jobs.add(task); }
            return task;
        });
        when(tasks.findById(anyLong())).thenAnswer(call -> jobs.stream().filter(t -> t.getId().equals(call.getArgument(0))).findFirst());
        when(tasks.findPoiIdById(anyLong())).thenAnswer(call -> jobs.stream().filter(t -> t.getId().equals(call.getArgument(0))).map(PipelineTask::getPoiId).findFirst());
        when(tasks.findByPoiId(1L)).thenAnswer(call -> new ArrayList<>(jobs));
        when(tasks.findFirstByPoiIdAndTargetLanguageCodeOrderByIdDesc(eq(1L), anyString())).thenAnswer(call -> jobs.stream()
                .filter(t -> t.getTargetLanguageCode().equals(call.getArgument(1))).max(Comparator.comparing(PipelineTask::getId)));
        when(tasks.existsByPoiIdAndTargetLanguageCodeAndStatusIn(eq(1L), anyString(), anyCollection())).thenAnswer(call -> jobs.stream()
                .anyMatch(t -> t.getTargetLanguageCode().equals(call.getArgument(1)) && ((Collection<?>) call.getArgument(2)).contains(t.getStatus())));
        when(tasks.findDispatchableIds(any(), any())).thenAnswer(call -> jobs.stream().filter(t -> "PENDING".equals(t.getStatus())
                || ("PROCESSING".equals(t.getStatus()) && t.getLeaseUntil().isBefore(call.getArgument(0)))).map(PipelineTask::getId).toList());
        when(generator.provider(anyString())).thenReturn("Google Cloud TTS");
        when(generator.supportsLanguage(anyString())).thenReturn(true);
        when(generator.hasUsableFile(anyString())).thenReturn(true);
    }

    PipelineTask latest(String code) { return jobs.stream().filter(t -> t.getTargetLanguageCode().equals(code)).max(Comparator.comparing(PipelineTask::getId)).orElseThrow(); }
    void enqueue() { service.enqueueSource(poi); }
    TranslationReviewService.Work claim(String code) { return service.claim(latest(code).getId()); }
    TranslationReviewService.Work translate(String code, String content) { return service.saveTranslation(claim(code), content); }
    void finish(String code, String content) {
        var work = translate(code, content);
        service.complete(work, new AudioGenerationService.GeneratedAudio("/storage/audio/" + code + ".wav", 20));
    }
}
