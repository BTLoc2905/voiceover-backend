package com.voiceover.backend.services;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class PipelineDispatcherTest {
    final TranslationReviewService jobs = mock(TranslationReviewService.class);
    final PipelineService worker = mock(PipelineService.class);
    final PipelineDispatcher dispatcher = new PipelineDispatcher(jobs, worker);

    @Test void pollingDeliversAcceptedWorkEvenWhenTheCommitEventWasLost() {
        when(jobs.dispatchableIds()).thenReturn(List.of(10L));
        when(worker.resumeTask(10L)).thenReturn(CompletableFuture.completedFuture(null));
        dispatcher.poll(); verify(worker).resumeTask(10L);
        verifyNoMoreInteractions(worker); verify(jobs, never()).fail(any(), anyBoolean(), anyString());
    }

    @Test void executorRejectionLeavesTheJobAvailableForTheNextPoll() {
        when(jobs.dispatchableIds()).thenReturn(List.of(10L));
        when(worker.resumeTask(10L)).thenThrow(new TaskRejectedException("full")).thenReturn(CompletableFuture.completedFuture(null));
        dispatcher.afterCommit(new ReviewAudioRequested(10L)); dispatcher.poll();
        verify(worker, times(2)).resumeTask(10L); verify(jobs, never()).fail(any(), anyBoolean(), anyString());
    }

    @Test void localInflightDeliveryIsDeduplicatedUntilTheWorkerReturns() {
        when(jobs.dispatchableIds()).thenReturn(List.of(10L));
        CompletableFuture<Void> pending = new CompletableFuture<>(); when(worker.resumeTask(10L)).thenReturn(pending);
        dispatcher.afterCommit(new ReviewAudioRequested(10L)); dispatcher.poll();
        verify(worker).resumeTask(10L); pending.complete(null); dispatcher.poll();
        verify(worker, times(2)).resumeTask(10L);
    }
}
