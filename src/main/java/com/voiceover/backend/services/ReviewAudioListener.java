package com.voiceover.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
@RequiredArgsConstructor
public class ReviewAudioListener {
    private final ReviewAudioWorker worker;
    private final TranslationReviewService review;
    private final PipelineService pipeline;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ReviewAudioRequested event) {
        try { worker.generate(event.taskId()); }
        catch (org.springframework.core.task.TaskRejectedException error) {
            review.fail(event.taskId(), "Hàng đợi tạo audio đang đầy. Bản dịch đã lưu; hãy thử lại sau.", 0);
        }
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        review.interruptReviewJobs();
        for (Long taskId : pipeline.requeueInterruptedTasks()) {
            try { pipeline.resumeTask(taskId); }
            catch (org.springframework.core.task.TaskRejectedException error) {
                pipeline.failRecoveredTask(taskId, "Hàng đợi pipeline đang đầy. Hãy lưu lại POI để thử lại.");
            }
        }
    }
}
