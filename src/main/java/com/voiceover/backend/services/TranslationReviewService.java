package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import com.voiceover.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Short, POI-locked transactions only. Provider calls run outside these transactions. */
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
    private final ProtectedTermRepository terms;
    private final SystemLogRepository logs;
    private static final List<String> ACTIVE = List.of("PENDING", "PROCESSING");
    private static final int LEASE_SECONDS = 60;

    public record PoiView(Long id, String name, String status, String sourceLanguageCode, String descriptionSource, long contentRevision) {}
    public record TranslationView(Long id, String content, String status, boolean manualEdit, LocalDateTime updatedAt, String revision) {}
    public record AudioView(String status, String url, Integer durationSec, LocalDateTime updatedAt) {}
    public record TaskView(Long id, String type, String status, int retryCount, String error, LocalDateTime createdAt, LocalDateTime finishedAt, long sourceRevision) {}
    public record LanguageView(String code, String name, boolean active, boolean ttsSupported, String provider,
                               TranslationView translation, AudioView audio, TaskView task, boolean busy) {}
    public record Index(PoiView poi, List<LanguageView> languages) {}
    public record Detail(PoiView poi, LanguageView review) {}
    public record Work(Long taskId, Long poiId, String code, long sourceRevision, String sourceHash,
                       String sourceText, String sourceLanguage, List<String> terms, boolean ttsSupported,
                       String text, String translationRevision, String claimToken) {
        Work translated(Translation translation) {
            return new Work(taskId, poiId, code, sourceRevision, sourceHash, sourceText, sourceLanguage,
                    terms, ttsSupported, translation.getContent(), revision(translation), claimToken);
        }
    }

    @Transactional(readOnly = true)
    public Index index(Long poiId) {
        Poi poi = requirePoi(poiId);
        Map<String, Translation> text = translations.findByPoiId(poiId).stream().collect(Collectors.toMap(Translation::getLanguageCode, Function.identity()));
        Map<String, Audio> files = audios.findByPoiId(poiId).stream().collect(Collectors.toMap(Audio::getLanguageCode, Function.identity()));
        List<PipelineTask> jobs = tasks.findByPoiId(poiId);
        return new Index(poiView(poi), languages.findAll().stream().sorted(Comparator.comparing(Language::getCode))
                .map(lang -> view(lang, text.get(lang.getCode()), files.get(lang.getCode()), jobs.stream()
                        .filter(task -> lang.getCode().equals(task.getTargetLanguageCode())).toList())).toList());
    }

    @Transactional(readOnly = true)
    public Detail detail(Long poiId, String code) { return detail(requirePoi(poiId), requireLanguage(code)); }

    private Detail detail(Poi poi, Language language) {
        return new Detail(poiView(poi), view(language,
                translations.findByPoiIdAndLanguageCode(poi.getId(), language.getCode()).orElse(null),
                audios.findByPoiIdAndLanguageCode(poi.getId(), language.getCode()).orElse(null),
                tasks.findByPoiId(poi.getId()).stream().filter(t -> language.getCode().equals(t.getTargetLanguageCode())).toList()));
    }

    private LanguageView view(Language lang, Translation text, Audio file, List<PipelineTask> jobs) {
        PipelineTask latest = jobs.stream().max(Comparator.comparing(PipelineTask::getId)).orElse(null);
        TranslationView translation = text == null ? new TranslationView(null, "", "MISSING", false, null, null)
                : new TranslationView(text.getId(), text.getContent(), text.getStatus(), text.isManualEdit(), text.getUpdatedAt(), revision(text));
        AudioView audio = file == null ? new AudioView("MISSING", null, null, null)
                : new AudioView(file.getStatus(), "COMPLETED".equals(file.getStatus()) ? file.getFilePath() : null, file.getDurationSec(), file.getUpdatedAt());
        TaskView task = latest == null ? null : new TaskView(latest.getId(), latest.getTaskType(), latest.getStatus(), latest.getRetryCount(),
                latest.getErrorMessage(), latest.getCreatedAt(), latest.getFinishedAt(), latest.getSourceRevision());
        boolean audioSupported = supportsAudio(lang);
        return new LanguageView(lang.getCode(), lang.getName(), lang.isActive(), audioSupported,
                audioSupported ? generator.provider(lang.getCode()) : "UNSUPPORTED",
                translation, audio, task, jobs.stream().anyMatch(job -> ACTIVE.contains(job.getStatus())));
    }

    private boolean supportsAudio(Language language) {
        return language.isTtsSupported() && generator.supportsLanguage(language.getCode());
    }

    /** Called under the POI write lock, before a DRAFT can become public. */
    public void requireDefaultAudioForPublication(Poi proposed, Poi current) {
        if (current.getId() == null || current.getSourceFingerprint() == null)
            throw new IllegalArgumentException("Hãy lưu POI ở trạng thái Nháp và đợi audio ngôn ngữ mặc định hoàn tất trước khi xuất bản.");
        String defaultCode = languages.findByIsDefaultTrue().map(Language::getCode)
                .orElseThrow(() -> new IllegalArgumentException("Chưa cấu hình ngôn ngữ mặc định."));
        Poi normalized = Poi.builder().descriptionSource(proposed.getDescriptionSource().strip())
                .sourceLanguageCode(proposed.getSourceLanguageCode()).build();
        String sourceHash = ContentFingerprint.source(normalized, currentTerms());
        Translation translation = translations.findByPoiIdAndLanguageCode(current.getId(), defaultCode).orElse(null);
        Audio audio = audios.findByPoiIdAndLanguageCode(current.getId(), defaultCode).orElse(null);
        if (!sourceHash.equals(current.getSourceFingerprint()) || translation == null
                || !"COMPLETED".equals(translation.getStatus()) || !sourceHash.equals(translation.getSourceHash())
                || translation.getContent() == null || translation.getContent().isBlank()
                || audio == null || !"COMPLETED".equals(audio.getStatus())
                || !Objects.equals(audio.getTranslationId(), translation.getId())
                || !generator.hasUsableFile(audio.getFilePath()))
            throw new IllegalArgumentException("Chưa có file audio hoàn tất cho ngôn ngữ mặc định (" + defaultCode + ") của mô tả hiện tại. Hãy đợi pipeline hoặc tạo lại audio trước khi xuất bản.");
    }

    /** The source and every language job commit together. Caller holds the POI lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueSource(Poi poi) {
        List<String> snapshot = currentTerms();
        String hash = ContentFingerprint.source(poi, snapshot);
        boolean changed = !hash.equals(poi.getSourceFingerprint());
        if (changed) {
            poi.setContentRevision(poi.getContentRevision() + 1);
            poi.setSourceFingerprint(hash);
            pois.saveAndFlush(poi);
            for (PipelineTask old : tasks.findByPoiId(poi.getId())) {
                if (ACTIVE.contains(old.getStatus())) supersede(old);
            }
        }
        for (Language language : languages.findByIsActiveTrue()) {
            String code = language.getCode();
            boolean audioSupported = supportsAudio(language);
            if (tasks.existsByPoiIdAndTargetLanguageCodeAndStatusIn(poi.getId(), code, ACTIVE)) continue;
            Translation text = translations.findByPoiIdAndLanguageCode(poi.getId(), code)
                    .orElseGet(() -> Translation.builder().poiId(poi.getId()).languageCode(code).build());
            boolean reusable = hash.equals(text.getSourceHash()) && "COMPLETED".equals(text.getStatus())
                    && text.getContent() != null && !text.getContent().isBlank();
            Audio audio = audios.findByPoiIdAndLanguageCode(poi.getId(), code).orElse(null);
            if (reusable && audio != null && ((!audioSupported && "TEXT_ONLY".equals(audio.getStatus()))
                    || ("COMPLETED".equals(audio.getStatus()) && generator.hasUsableFile(audio.getFilePath())))) continue;
            PipelineTask previous = tasks.findFirstByPoiIdAndTargetLanguageCodeOrderByIdDesc(poi.getId(), code).orElse(null);
            if (!changed && text.isManualEdit() && previous != null && "TTS_ONLY".equals(previous.getTaskType())
                    && "FAILED".equals(previous.getStatus())) continue;
            PipelineTask task = newTask(poi, code, reusable ? "TTS_ONLY" : "TRANSLATE_TTS", snapshot);
            if (text.isManualEdit() && !reusable) {
                text.setStatus("STALE");
                text = translations.saveAndFlush(text);
                resetAudio(text, "FAILED");
                task.setStatus("FAILED"); task.setFinishedAt(LocalDateTime.now());
                task.setErrorMessage("Nguồn hoặc thuật ngữ đã đổi. Bản sửa tay được giữ; hãy rà soát và lưu lại bản dịch.");
            } else {
                if (!reusable) text.setStatus("PENDING");
                text = translations.saveAndFlush(text);
                if (reusable) task.setTranslationRevision(revision(text));
                resetAudio(text, "PENDING");
            }
            task = tasks.saveAndFlush(task);
            if ("PENDING".equals(task.getStatus())) events.publishEvent(new ReviewAudioRequested(task.getId()));
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Detail submit(Long poiId, String code, String content, String expectedRevision, boolean regenerateOnly) {
        Poi poi = pois.findForUpdate(poiId).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại."));
        Language language = requireLanguage(code);
        if (!language.isActive()) throw new IllegalArgumentException("Ngôn ngữ này đã ngừng hoạt động.");
        if (regenerateOnly && !supportsAudio(language))
            throw new IllegalArgumentException("Ngôn ngữ này không hỗ trợ tạo audio bằng TTS provider đang chọn.");
        if (tasks.existsByPoiIdAndTargetLanguageCodeAndStatusIn(poiId, code, ACTIVE))
            throw new ContentReviewConflictException("Ngôn ngữ này đang được xử lý. Đợi tác vụ kết thúc rồi thử lại.");
        Translation text = translations.findByPoiIdAndLanguageCode(poiId, code)
                .orElseThrow(() -> new IllegalArgumentException("Chưa có bản dịch cho ngôn ngữ này. Kiểm tra pipeline trước khi hiệu chỉnh."));
        if (expectedRevision == null || !expectedRevision.equals(revision(text)))
            throw new ContentReviewConflictException("Bản dịch đã thay đổi. Tải lại trước khi lưu; bản đang soạn chưa bị ghi đè.");
        List<String> snapshot = currentTerms();
        String hash = ContentFingerprint.source(poi, snapshot);
        if (!hash.equals(poi.getSourceFingerprint()))
            throw new ContentReviewConflictException("Nguồn hoặc thuật ngữ đã đổi hoặc chưa có phiên bản. Lưu lại POI và tải bản dịch mới trước khi hiệu chỉnh.");
        if (regenerateOnly) {
            if (!"COMPLETED".equals(text.getStatus()) || !hash.equals(text.getSourceHash())
                    || text.getContent() == null || text.getContent().isBlank())
                throw new IllegalArgumentException("Bản dịch chưa khớp nguồn hiện tại. Hãy rà soát và lưu bản dịch trước khi tạo audio.");
        } else {
            if (content == null || content.isBlank()) throw new IllegalArgumentException("Nội dung bản dịch không được để trống.");
            content = content.strip();
            if (content.getBytes(StandardCharsets.UTF_8).length > 60000) throw new IllegalArgumentException("Bản dịch vượt giới hạn 60.000 byte UTF-8.");
            text.setContent(content); text.setManualEdit(true); text.setStatus("COMPLETED"); text.setSourceHash(hash);
            text = translations.saveAndFlush(text);
        }
        resetAudio(text, "PENDING");
        PipelineTask task = newTask(poi, code, "TTS_ONLY", snapshot);
        task.setTranslationRevision(revision(text)); task = tasks.saveAndFlush(task);
        events.publishEvent(new ReviewAudioRequested(task.getId()));
        return detail(poi, language);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Long> dispatchableIds() { return tasks.findDispatchableIds(LocalDateTime.now(), PageRequest.of(0, 100)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Work claim(Long taskId) {
        PipelineTask task = lockedTask(taskId);
        if (task == null || (!"PENDING".equals(task.getStatus()) && !("PROCESSING".equals(task.getStatus())
                && task.getLeaseUntil() != null && task.getLeaseUntil().isBefore(LocalDateTime.now())))) return null;
        Poi poi = requirePoi(task.getPoiId());
        if (!current(task, poi) || !latest(task)) { supersede(task); return null; }
        Language language = requireLanguage(task.getTargetLanguageCode());
        if (!language.isActive()) { failTask(task, "Ngôn ngữ đã ngừng hoạt động."); return null; }
        Translation text = translations.findByPoiIdAndLanguageCode(poi.getId(), language.getCode()).orElseThrow();
        boolean ready = task.getSourceHash().equals(text.getSourceHash()) && "COMPLETED".equals(text.getStatus())
                && text.getContent() != null && !text.getContent().isBlank();
        if ("TTS_ONLY".equals(task.getTaskType()) && (!ready || !Objects.equals(task.getTranslationRevision(), revision(text)))) {
            failTask(task, "Bản dịch đã thay đổi; hãy rà soát trước khi tạo audio."); return null;
        }
        task.setStatus("PROCESSING"); task.setClaimToken(UUID.randomUUID().toString());
        task.setLeaseUntil(LocalDateTime.now().plusSeconds(LEASE_SECONDS)); task.setErrorMessage(null);
        if (!ready) { text.setStatus("PROCESSING"); translations.saveAndFlush(text); }
        else task.setTranslationRevision(revision(text));
        resetAudio(text, "PROCESSING"); tasks.saveAndFlush(task);
        return new Work(taskId, poi.getId(), language.getCode(), task.getSourceRevision(), task.getSourceHash(),
                task.getSourceText(), task.getSourceLanguageCode(), ContentFingerprint.readTerms(task.getProtectedTermsJson()),
                supportsAudio(language), ready ? text.getContent() : null, ready ? revision(text) : null, task.getClaimToken());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Work saveTranslation(Work work, String content) {
        PipelineTask task = owned(work);
        if (task == null) return null;
        if (content == null || content.isBlank() || content.getBytes(StandardCharsets.UTF_8).length > 60000)
            throw new IllegalArgumentException("Bản dịch rỗng hoặc vượt giới hạn 60.000 byte UTF-8.");
        Translation text = translations.findByPoiIdAndLanguageCode(work.poiId(), work.code()).orElseThrow();
        text.setContent(content); text.setSourceHash(work.sourceHash()); text.setManualEdit(false); text.setStatus("COMPLETED");
        text = translations.saveAndFlush(text); task.setTranslationRevision(revision(text)); tasks.saveAndFlush(task);
        return work.translated(text);
    }

    /** Persist attempts BEFORE contacting the provider, including across a crash/reclaim. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int nextAttempt(Work work, boolean translating) {
        PipelineTask task = owned(work);
        if (task == null) return 0;
        int count = (translating ? task.getTranslationAttempts() : task.getAudioAttempts()) + 1;
        if (count > 3) { failOwned(task, translating, "Đã hết 3 lần gọi dịch vụ. Hãy chủ động thử lại."); return 0; }
        if (translating) task.setTranslationAttempts(count); else task.setAudioAttempts(count);
        task.setRetryCount(count - 1); tasks.saveAndFlush(task); return count;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean complete(Work work, AudioGenerationService.GeneratedAudio generated) {
        PipelineTask task = owned(work);
        if (task == null) return false;
        Translation text = translations.findByPoiIdAndLanguageCode(work.poiId(), work.code()).orElseThrow();
        if (!Objects.equals(work.translationRevision(), revision(text)) || !"COMPLETED".equals(text.getStatus())) {
            failOwned(task, false, "Bản dịch đã đổi khi đang tạo audio."); return false;
        }
        Audio audio = audios.findByPoiIdAndLanguageCode(work.poiId(), work.code()).orElseThrow();
        audio.setStatus(generated == null ? "TEXT_ONLY" : "COMPLETED");
        audio.setFilePath(generated == null ? null : generated.url());
        audio.setDurationSec(generated == null ? null : generated.durationSec()); audios.saveAndFlush(audio);
        task.setStatus("COMPLETED"); task.setErrorMessage(null); finish(task); return true;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void fail(Work work, boolean translating, String message) {
        PipelineTask task = owned(work);
        if (task != null) failOwned(task, translating, message);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void renewLease(Work work) {
        PipelineTask task = owned(work);
        if (task != null) { task.setLeaseUntil(LocalDateTime.now().plusSeconds(LEASE_SECONDS)); tasks.saveAndFlush(task); }
    }

    private PipelineTask newTask(Poi poi, String code, String type, List<String> snapshot) {
        return PipelineTask.builder().poiId(poi.getId()).targetLanguageCode(code).taskType(type).status("PENDING")
                .sourceRevision(poi.getContentRevision()).sourceHash(poi.getSourceFingerprint())
                .sourceText(poi.getDescriptionSource()).sourceLanguageCode(poi.getSourceLanguageCode())
                .protectedTermsJson(ContentFingerprint.json(snapshot)).createdAt(LocalDateTime.now()).build();
    }

    private Audio resetAudio(Translation text, String status) {
        Audio audio = audios.findByPoiIdAndLanguageCode(text.getPoiId(), text.getLanguageCode())
                .orElseGet(() -> Audio.builder().poiId(text.getPoiId()).languageCode(text.getLanguageCode()).build());
        audio.setTranslationId(text.getId()); audio.setStatus(status); audio.setFilePath(null); audio.setDurationSec(null);
        return audios.saveAndFlush(audio);
    }

    private PipelineTask owned(Work work) {
        PipelineTask task = lockedTask(work.taskId());
        if (task == null || !"PROCESSING".equals(task.getStatus()) || !Objects.equals(work.claimToken(), task.getClaimToken())) return null;
        if (!current(task, requirePoi(work.poiId())) || !latest(task)) { supersede(task); return null; }
        return task;
    }

    private boolean current(PipelineTask task, Poi poi) {
        if (task.getSourceHash() == null || task.getSourceRevision() != poi.getContentRevision()
                || !Objects.equals(task.getSourceHash(), poi.getSourceFingerprint())) return false;
        return task.getSourceHash().equals(ContentFingerprint.source(poi, ContentFingerprint.readTerms(task.getProtectedTermsJson())));
    }

    private void failOwned(PipelineTask task, boolean translating, String message) {
        Translation text = translations.findByPoiIdAndLanguageCode(task.getPoiId(), task.getTargetLanguageCode()).orElse(null);
        if (text != null) {
            if (translating) { text.setStatus("FAILED"); translations.saveAndFlush(text); }
            resetAudio(text, "FAILED");
        }
        failTask(task, message);
    }

    private void failTask(PipelineTask task, String message) {
        task.setStatus("FAILED"); task.setErrorMessage(message); finish(task);
        logs.save(SystemLog.builder().component("pipeline").severity("ERROR").message(message).build());
    }

    private void supersede(PipelineTask task) {
        task.setStatus("FAILED"); task.setErrorMessage("Đã được thay thế bởi phiên bản nội dung mới; kết quả cũ không được sử dụng."); finish(task);
    }

    private void finish(PipelineTask task) {
        task.setFinishedAt(LocalDateTime.now()); task.setClaimToken(null); task.setLeaseUntil(null); tasks.saveAndFlush(task);
    }

    private PipelineTask lockedTask(Long id) {
        Long poiId = tasks.findPoiIdById(id).orElse(null);
        if (poiId == null || pois.findForUpdate(poiId).isEmpty()) return null;
        return tasks.findById(id).orElse(null);
    }

    private boolean latest(PipelineTask task) {
        return tasks.findFirstByPoiIdAndTargetLanguageCodeOrderByIdDesc(task.getPoiId(), task.getTargetLanguageCode())
                .map(t -> t.getId().equals(task.getId())).orElse(false);
    }

    private List<String> currentTerms() { return ContentFingerprint.terms(terms.findAll().stream().map(ProtectedTerm::getTerm).toList()); }
    private Poi requirePoi(Long id) { return pois.findById(id).orElseThrow(() -> new IllegalArgumentException("POI không tồn tại.")); }
    private Language requireLanguage(String code) { return languages.findById(code).orElseThrow(() -> new IllegalArgumentException("Ngôn ngữ không tồn tại.")); }
    private PoiView poiView(Poi poi) { return new PoiView(poi.getId(), poi.getName(), poi.getStatus(), poi.getSourceLanguageCode(), poi.getDescriptionSource(), poi.getContentRevision()); }
    public static String revision(Translation text) {
        return ContentFingerprint.hash(ContentFingerprint.json(Arrays.asList(text.getId(), text.getStatus(), text.isManualEdit(), text.getSourceHash(), text.getContent())));
    }
}
