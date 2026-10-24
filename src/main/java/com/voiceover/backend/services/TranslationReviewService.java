package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TranslationReviewService {
    private final PoiRepository pois;
    private final LanguageRepository languages;
    private final TranslationRepository translations;
    private final AudioRepository audios;
    private final PipelineTaskRepository tasks;
    private final ApplicationEventPublisher events;
    private final AudioGenerationService generator;
    private static final List<String> ACTIVE = List.of("PENDING", "PROCESSING");
    private final LocalDateTime startedAt = LocalDateTime.now();
    public record PoiView(Long id, String name, String status, String sourceLanguageCode, String descriptionSource) {}
    public record TranslationView(Long id, String content, String status, boolean manualEdit, LocalDateTime updatedAt, String revision) {}
    public record AudioView(String status, String url, Integer durationSec, LocalDateTime updatedAt) {}
    public record TaskView(Long id, String type, String status, int retryCount, String error, LocalDateTime createdAt, LocalDateTime finishedAt) {}
    public record LanguageView(String code, String name, boolean active, boolean ttsSupported, String provider,
                               TranslationView translation, AudioView audio, TaskView task, boolean busy) {}
    public record Index(PoiView poi, List<LanguageView> languages) {}
    public record Detail(PoiView poi, LanguageView review) {}
    public record AudioWork(Long taskId, Long poiId, Long translationId, String languageCode, String text, String revision, boolean ttsSupported) {}

    @Transactional(readOnly = true)
    public Index index(Long poiId) {
        Poi poi = requirePoi(poiId);
        Map<String, Translation> text = translations.findByPoiId(poiId).stream().collect(Collectors.toMap(Translation::getLanguageCode, Function.identity()));
        Map<String, Audio> files = audios.findByPoiId(poiId).stream().collect(Collectors.toMap(Audio::getLanguageCode, Function.identity()));
        List<PipelineTask> jobs = tasks.findByPoiId(poiId);
        List<LanguageView> rows = languages.findAll().stream().sorted(Comparator.comparing(Language::getCode))
                .map(lang -> view(lang, text.get(lang.getCode()), files.get(lang.getCode()), jobs.stream()
                        .filter(task -> lang.getCode().equals(task.getTargetLanguageCode())).toList())).toList();
        return new Index(poiView(poi), rows);
    }

    @Transactional(readOnly = true)
    public Detail detail(Long poiId, String code) {
        Poi poi = requirePoi(poiId); Language language = requireLanguage(code);
        return detail(poi, language);
    }
    private Detail detail(Poi poi, Language language) {
        return new Detail(poiView(poi), view(language,
                translations.findByPoiIdAndLanguageCode(poi.getId(), language.getCode()).orElse(null),
                audios.findByPoiIdAndLanguageCode(poi.getId(), language.getCode()).orElse(null),
                tasks.findByPoiId(poi.getId()).stream().filter(t -> language.getCode().equals(t.getTargetLanguageCode())).toList()));
    }
    private LanguageView view(Language lang, Translation text, Audio file, List<PipelineTask> jobs) {
        PipelineTask latest = jobs.stream().max(Comparator.comparing(PipelineTask::getId)).orElse(null);
        boolean busy = jobs.stream().anyMatch(job -> ACTIVE.contains(job.getStatus()));
        TranslationView translation = text == null ? new TranslationView(null, "", "MISSING", false, null, null)
                : new TranslationView(text.getId(), text.getContent(), text.getStatus(), text.isManualEdit(), text.getUpdatedAt(), revision(text));
        String url = file != null && "COMPLETED".equals(file.getStatus()) ? file.getFilePath() : null;
        AudioView audio = file == null ? new AudioView("MISSING", null, null, null)
                : new AudioView(file.getStatus(), url, file.getDurationSec(), file.getUpdatedAt());
        TaskView task = latest == null ? null : new TaskView(latest.getId(), latest.getTaskType(), latest.getStatus(), latest.getRetryCount(),
                latest.getErrorMessage(), latest.getCreatedAt(), latest.getFinishedAt());
        return new LanguageView(lang.getCode(), lang.getName(), lang.isActive(), lang.isTtsSupported(), generator.provider(lang.getCode()), translation, audio, task, busy);
    }

    /** POI row serializes edits/job reservations for the same POI across requests. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Detail submit(Long poiId, String code, String content, String expectedRevision, boolean regenerateOnly) {
        Poi poi = pois.findForUpdate(poiId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
        Language language = requireLanguage(code);
        if (!language.isActive()) throw new IllegalArgumentException("Ngôn ngữ này đã ngừng hoạt động.");
        if (tasks.existsByPoiIdAndTargetLanguageCodeAndStatusIn(poiId, code, ACTIVE))
            throw new ContentReviewConflictException("Ngôn ngữ này đang được xử lý. Đợi tác vụ hiện tại kết thúc rồi thử lại.");
        Translation text = translations.findByPoiIdAndLanguageCode(poiId, code)
                .orElseThrow(() -> new IllegalArgumentException("Chưa có bản dịch cho ngôn ngữ này. Kiểm tra pipeline trước khi hiệu chỉnh."));
        if (expectedRevision == null || !expectedRevision.equals(revision(text)))
            throw new ContentReviewConflictException("Bản dịch đã thay đổi hoặc phiên bản chưa được tải. Tải lại nội dung trước khi lưu; bản đang soạn chưa bị ghi đè.");
        if (regenerateOnly) {
            if (!"COMPLETED".equals(text.getStatus()) || text.getContent() == null || text.getContent().isBlank())
                throw new IllegalArgumentException("Bản dịch chưa sẵn sàng để tạo audio.");
        } else {
            if (content == null || content.isBlank()) throw new IllegalArgumentException("Nội dung bản dịch không được để trống.");
            content = content.strip();
            if (content.getBytes(StandardCharsets.UTF_8).length > 60000) throw new IllegalArgumentException("Bản dịch vượt giới hạn 60.000 byte UTF-8.");
            text.setContent(content); text.setManualEdit(true); text.setStatus("COMPLETED");
            text.setSourceHash(hash(poi.getDescriptionSource() == null ? "" : poi.getDescriptionSource()));
            text = translations.saveAndFlush(text);
        }
        Audio audio = audios.findByPoiIdAndLanguageCode(poiId, code).orElseGet(() -> Audio.builder().poiId(poiId).languageCode(code).build());
        audio.setTranslationId(text.getId()); audio.setStatus("PENDING"); audio.setFilePath(null); audio.setDurationSec(null);
        audios.saveAndFlush(audio);
        PipelineTask task = tasks.saveAndFlush(PipelineTask.builder().poiId(poiId).targetLanguageCode(code).taskType("TTS_ONLY")
                .status("PENDING").retryCount(0).createdAt(LocalDateTime.now()).build());
        events.publishEvent(new ReviewAudioRequested(task.getId()));
        return detail(poi, language);
    }

    /** Full pipeline cooperates with review jobs, rather than overwriting a queued manual edit. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PipelineTask reserveTranslation(Long poiId, String code, String sourceHash) {
        Poi poi = pois.findForUpdate(poiId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
        if (!sourceHash.equals(hash(poi.getDescriptionSource() == null ? "" : poi.getDescriptionSource()))) return null;
        if (tasks.existsByPoiIdAndTargetLanguageCodeAndStatusIn(poiId, code, ACTIVE)) return null;
        Translation existing = translations.findByPoiIdAndLanguageCode(poiId, code).orElse(null);
        if (existing != null && sourceHash.equals(existing.getSourceHash()) && "COMPLETED".equals(existing.getStatus())) return null;
        return tasks.saveAndFlush(PipelineTask.builder().poiId(poiId).targetLanguageCode(code).taskType("TRANSLATE_TTS")
                .status("PROCESSING").retryCount(0).createdAt(LocalDateTime.now()).build());
    }

    public void assertSourceEditable(Long poiId) {
        if (tasks.existsByPoiIdAndTaskTypeAndStatusIn(poiId, "TTS_ONLY", ACTIVE))
            throw new ContentReviewConflictException("POI đang tạo audio từ bản hiệu chỉnh. Đợi tác vụ kết thúc trước khi đổi mô tả gốc/ngôn ngữ gốc.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AudioWork begin(Long taskId) {
        PipelineTask task = lockedTask(taskId);
        if (task == null || !"PENDING".equals(task.getStatus())) return null;
        Translation text = translations.findByPoiIdAndLanguageCode(task.getPoiId(), task.getTargetLanguageCode()).orElseThrow();
        Language language = requireLanguage(task.getTargetLanguageCode());
        task.setStatus("PROCESSING"); tasks.saveAndFlush(task);
        Audio audio = audios.findByPoiIdAndLanguageCode(task.getPoiId(), task.getTargetLanguageCode()).orElseThrow();
        audio.setStatus("PROCESSING"); audios.saveAndFlush(audio);
        return new AudioWork(taskId, task.getPoiId(), text.getId(), text.getLanguageCode(), text.getContent(), revision(text), language.isTtsSupported());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(AudioWork work, AudioGenerationService.GeneratedAudio generated, int retries) {
        PipelineTask task = lockedTask(work.taskId());
        if (task == null || !"PROCESSING".equals(task.getStatus())) return;
        Translation current = translations.findByPoiIdAndLanguageCode(work.poiId(), work.languageCode()).orElse(null);
        if (current == null || !work.revision().equals(revision(current)) || !latest(task)) {
            if (latest(task)) audios.findByPoiIdAndLanguageCode(work.poiId(), work.languageCode()).ifPresent(audio -> {
                audio.setStatus("FAILED"); audio.setFilePath(null); audio.setDurationSec(null); audios.saveAndFlush(audio);
            });
            task.setStatus("FAILED"); task.setErrorMessage("Bản dịch đã thay đổi khi đang tạo audio. Hãy tải lại và tạo lại audio.");
            task.setFinishedAt(LocalDateTime.now()); tasks.saveAndFlush(task); return;
        }
        Audio audio = audios.findByPoiIdAndLanguageCode(work.poiId(), work.languageCode()).orElseThrow();
        audio.setStatus(generated == null ? "TEXT_ONLY" : "COMPLETED"); audio.setFilePath(generated == null ? null : generated.url());
        audio.setDurationSec(generated == null ? null : generated.durationSec()); audios.saveAndFlush(audio);
        task.setStatus("COMPLETED"); task.setRetryCount(retries); task.setErrorMessage(null); task.setFinishedAt(LocalDateTime.now()); tasks.saveAndFlush(task);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long taskId, String message, int retries) {
        PipelineTask task = lockedTask(taskId);
        if (task == null || !ACTIVE.contains(task.getStatus())) return;
        if (latest(task)) audios.findByPoiIdAndLanguageCode(task.getPoiId(), task.getTargetLanguageCode()).ifPresent(audio -> {
            audio.setStatus("FAILED"); audio.setFilePath(null); audio.setDurationSec(null); audios.saveAndFlush(audio);
        });
        task.setStatus("FAILED"); task.setRetryCount(retries); task.setErrorMessage(message); task.setFinishedAt(LocalDateTime.now()); tasks.saveAndFlush(task);
    }

    /** Single-instance recovery: interrupted UC09 jobs become retryable instead of staying PROCESSING forever. */
    @Transactional
    public void interruptReviewJobs() {
        for (PipelineTask task : tasks.findByTaskTypeAndStatusIn("TTS_ONLY", ACTIVE))
            if (task.getCreatedAt() == null || task.getCreatedAt().isBefore(startedAt))
                fail(task.getId(), "Tác vụ bị gián đoạn khi máy chủ khởi động lại. Bản dịch vẫn được giữ; hãy tạo lại audio.", task.getRetryCount());
    }
    private PipelineTask lockedTask(Long id) {
        Long poiId = tasks.findPoiIdById(id).orElse(null);
        if (poiId == null) return null;
        pois.findForUpdate(poiId).orElseThrow();
        return tasks.findById(id).orElse(null);
    }
    private boolean latest(PipelineTask task) {
        return tasks.findFirstByPoiIdAndTargetLanguageCodeOrderByIdDesc(task.getPoiId(), task.getTargetLanguageCode()).map(t -> t.getId().equals(task.getId())).orElse(false);
    }
    private Poi requirePoi(Long id) { return pois.findById(id).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại.")); }
    private Language requireLanguage(String code) { return languages.findById(code).orElseThrow(() -> new IllegalArgumentException("Ngôn ngữ không tồn tại.")); }
    private PoiView poiView(Poi poi) { return new PoiView(poi.getId(), poi.getName(), poi.getStatus(), poi.getSourceLanguageCode(), poi.getDescriptionSource()); }
    public static String revision(Translation text) { return hash(text.getId() + "|" + text.getStatus() + "|" + text.isManualEdit() + "|" + text.getSourceHash() + "|" + text.getContent()); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("Không tính được phiên bản nội dung.", error); }
    }
}
