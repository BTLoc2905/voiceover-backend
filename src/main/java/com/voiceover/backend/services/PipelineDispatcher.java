package com.voiceover.backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import java.util.HashSet;
import java.util.Set;

/** Events reduce latency; polling is the durable delivery path after lost events/restart/full executor. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PipelineDispatcher {
    private final TranslationReviewService jobs;
    private final PipelineService worker;
    private final Set<Long> submitted = new HashSet<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ReviewAudioRequested event) { dispatch(event.taskId()); }

    @Scheduled(fixedDelayString = "${app.pipeline.poll-ms:1000}", initialDelay = 1000)
    public void poll() {
        try { for (Long id : jobs.dispatchableIds()) dispatch(id); }
        catch (Exception error) { log.warn("Không đọc được hàng đợi pipeline; sẽ thử lại", error); }
    }

    private void dispatch(Long id) {
        synchronized (submitted) {
            if (submitted.size() >= 20 || !submitted.add(id)) return;
        }
        try {
            worker.resumeTask(id).whenComplete((ignored, error) -> {
                synchronized (submitted) { submitted.remove(id); }
                if (error != null) log.warn("Worker task {} dừng; task trong DB sẽ được nhận lại", id, error);
            });
        } catch (org.springframework.core.task.TaskRejectedException error) {
            synchronized (submitted) { submitted.remove(id); }
            // Keep PENDING. A queue-capacity problem must never erase accepted work.
        }
    }
}
