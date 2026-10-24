package com.voiceover.backend.services;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.mockito.Mockito.*;

class ReviewAudioWorkerTest {
    final TranslationReviewService service=mock(TranslationReviewService.class);
    final AudioGenerationService generator=mock(AudioGenerationService.class);
    final ReviewAudioWorker worker=new ReviewAudioWorker(service,generator);
    TranslationReviewService.AudioWork work(boolean supported) { return new TranslationReviewService.AudioWork(10L,1L,2L,"vi","Full content","revision",supported); }
    @Test void retriesAtMostThreeAndPublishesOnlySuccessfulOutput() throws Exception {
        var job=work(true); var result=new AudioGenerationService.GeneratedAudio("/new.wav",5);
        when(service.begin(10L)).thenReturn(job);
        when(generator.generate(1L,"vi","Full content")).thenThrow(new IOException("temporary")).thenThrow(new IOException("temporary")).thenReturn(result);
        worker.generate(10L); verify(generator,times(3)).generate(1L,"vi","Full content"); verify(service).complete(job,result,2); verify(service,never()).fail(any(),any(),anyInt());
    }
    @Test void missingProviderFailsHonestlyAndLeavesRetryableState() throws Exception {
        when(service.begin(10L)).thenReturn(work(true)); when(generator.generate(any(),any(),any())).thenThrow(new IllegalStateException("Chưa cấu hình TTS"));
        worker.generate(10L); verify(generator,times(1)).generate(any(),any(),any()); verify(service).fail(10L,"Chưa cấu hình TTS",0); verify(service,never()).complete(any(),any(),anyInt());
    }
    @Test void unsupportedLanguageNeverCallsTheProvider() throws Exception {
        var job=work(false); when(service.begin(10L)).thenReturn(job); worker.generate(10L);
        verifyNoInteractions(generator); verify(service).complete(job,null,0);
    }
    @Test void textOnlyStorageFailureBecomesRetryable() {
        var job=work(false); when(service.begin(10L)).thenReturn(job);
        doThrow(new IllegalStateException("database unavailable")).when(service).complete(job,null,0);
        worker.generate(10L);
        verify(service).fail(eq(10L),contains("Không lưu được trạng thái"),eq(0)); verifyNoInteractions(generator);
    }
}
