package com.voiceover.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewAudioWorker {
    private final TranslationReviewService review;
    private final AudioGenerationService generator;

    @Async("pipelineExecutor")
    public void generate(Long taskId) {
        TranslationReviewService.AudioWork work;
        try { work = review.begin(taskId); }
        catch (Exception error) { review.fail(taskId, "Không đọc được nội dung để tạo audio. Hãy tải lại và thử lại.", 0); return; }
        if (work == null) return;
        if (!work.ttsSupported()) {
            try { review.complete(work, null, 0); }
            catch (Exception error) { review.fail(taskId, "Không lưu được trạng thái chỉ văn bản. Bản dịch vẫn được giữ; hãy thử lại.", 0); }
            return;
        }
        AudioGenerationService.GeneratedAudio generated = null;
        int failures = 0; String message = "Không tạo được audio. Kiểm tra dịch vụ TTS rồi thử lại.";
        while (failures < 3) {
            try { generated = generator.generate(work.poiId(), work.languageCode(), work.text()); break; }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); message = "Tạo audio bị gián đoạn. Hãy thử lại."; failures++; break; }
            catch (Exception error) {
                failures++;
                if (error instanceof IllegalStateException || error instanceof java.io.IOException) message = error.getMessage();
            }
        }
        if (generated == null) { review.fail(taskId, message, failures); return; }
        try { review.complete(work, generated, failures); }
        catch (Exception error) { review.fail(taskId, "Không lưu được kết quả audio. Bản dịch vẫn được giữ; hãy thử lại.", failures); }
    }
}
