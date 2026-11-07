package com.voiceover.backend.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in external-service check; never runs in the default offline suite. */
@EnabledIfSystemProperty(named = "edge.tts.live", matches = "true")
class AudioGenerationServiceEdgeLiveTest {
    @TempDir Path storage;

    @Test void javaWorkerCreatesRealVietnameseAndEnglishMp3() throws Exception {
        var generator = new AudioGenerationService("", "edge", ".venv-edge-tts/Scripts/python.exe",
                "scripts/edge_tts_worker.py", 90, storage.toString(), "https://unused.example");
        for (String language : new String[] {"vi", "en"}) {
            String text = "vi".equals(language) ? "Xin chào. Đây là bài thử âm thanh tiếng Việt."
                    : "Hello. This is a real English audio test.";
            var audio = generator.generate(123L, language, text);
            assertTrue(audio.url().endsWith(".mp3"));
            assertTrue(audio.durationSec() > 0);
            assertTrue(generator.hasUsableFile(audio.url()));
            Path file = storage.resolve(audio.url().substring(audio.url().lastIndexOf('/') + 1));
            assertTrue(Files.size(file) > 1000);
        }
    }
}
