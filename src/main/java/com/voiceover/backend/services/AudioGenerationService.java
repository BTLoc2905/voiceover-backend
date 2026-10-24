package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Generates a versioned, complete file. No database writes or hidden text truncation. */
@Service
public class AudioGenerationService {
    private final String googleKey, piperExecutable, piperModel, piperLanguage, synthesizeUrl;
    private final Path storage;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    public record GeneratedAudio(String url, int durationSec) {}
    private static final Map<String, String> LOCALES = Map.ofEntries(
            Map.entry("vi", "vi-VN"), Map.entry("en", "en-US"), Map.entry("fr", "fr-FR"), Map.entry("de", "de-DE"),
            Map.entry("es", "es-ES"), Map.entry("it", "it-IT"), Map.entry("ja", "ja-JP"), Map.entry("ko", "ko-KR"),
            Map.entry("zh-CN", "cmn-CN"), Map.entry("zh-TW", "cmn-TW"), Map.entry("th", "th-TH"), Map.entry("ru", "ru-RU"),
            Map.entry("id", "id-ID"), Map.entry("pt", "pt-BR"), Map.entry("ar", "ar-XA"), Map.entry("ms", "ms-MY"), Map.entry("hi", "hi-IN"));

    public AudioGenerationService(@Value("${google.cloud.api.key:}") String googleKey,
            @Value("${app.piper.executable:}") String piperExecutable, @Value("${app.piper.model:}") String piperModel,
            @Value("${app.piper.language:vi}") String piperLanguage, @Value("${app.audio.storage:storage/audio}") String storage,
            @Value("${app.tts.synthesize-url:https://texttospeech.googleapis.com/v1/text:synthesize}") String synthesizeUrl) {
        this.googleKey = googleKey; this.piperExecutable = piperExecutable; this.piperModel = piperModel;
        this.piperLanguage = piperLanguage; this.storage = Paths.get(storage).toAbsolutePath().normalize(); this.synthesizeUrl = synthesizeUrl;
    }

    public String provider(String language) {
        if (!googleKey.isBlank()) return "Google Cloud TTS";
        if (!piperExecutable.isBlank() && !piperModel.isBlank() && piperLanguage.equalsIgnoreCase(language)) return "Piper";
        return "UNAVAILABLE";
    }

    public GeneratedAudio generate(Long poiId, String language, String text) throws Exception {
        if (!language.matches("[A-Za-z0-9-]{1,10}")) throw new IllegalArgumentException("Mã ngôn ngữ không hợp lệ.");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Bản dịch chưa có nội dung.");
        if (provider(language).equals("UNAVAILABLE")) throw new IllegalStateException("Chưa cấu hình dịch vụ tạo file audio cho ngôn ngữ này. Bản dịch đã lưu; có thể nghe thử bằng giọng trình duyệt nếu thiết bị hỗ trợ.");
        Files.createDirectories(storage);
        String name = "poi_" + poiId + "_" + language + "_" + UUID.randomUUID() + ".wav";
        Path destination = storage.resolve(name), temporary = storage.resolve(name + ".partial");
        try {
            if (!googleKey.isBlank()) google(text, language, temporary); else piper(text, temporary);
            int duration;
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(temporary.toFile())) {
                if (audio.getFrameLength() <= 0) throw new IOException("Dịch vụ không tạo được nội dung audio hợp lệ.");
                duration = Math.max(1, (int) Math.ceil(audio.getFrameLength() / audio.getFormat().getFrameRate()));
            }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination); }
            return new GeneratedAudio("/storage/audio/" + name, duration);
        } finally { Files.deleteIfExists(temporary); }
    }

    /** Each request stays below Google's 5,000 UTF-8 byte limit; joining retains every character. */
    public static List<String> chunks(String text) {
        List<String> result = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int end = offset, bytes = 0, boundary = -1;
            while (end < text.length()) {
                int point = text.codePointAt(end);
                int size = point <= 0x7f ? 1 : point <= 0x7ff ? 2 : point <= 0xffff ? 3 : 4;
                if (bytes + size > 4500) break;
                bytes += size; end += Character.charCount(point);
                if (Character.isWhitespace(point) || ".!?。！？".indexOf(point) >= 0) boundary = end;
            }
            if (end < text.length() && boundary > offset) end = boundary;
            result.add(text.substring(offset, end)); offset = end;
        }
        return result;
    }

    private void google(String text, String language, Path temporary) throws Exception {
        // LINEAR16 contains a WAV header. Decode each container and write ONE WAV, not concatenated MP3 headers.
        try (RandomAccessFile output = new RandomAccessFile(temporary.toFile(), "rw")) {
            output.setLength(44); output.seek(44);
            AudioFormat format = null; long length = 0;
            for (String part : chunks(text)) {
                String body = mapper.writeValueAsString(Map.of("input", Map.of("text", part),
                        "voice", Map.of("languageCode", LOCALES.getOrDefault(language, language), "ssmlGender", "NEUTRAL"),
                        "audioConfig", Map.of("audioEncoding", "LINEAR16", "sampleRateHertz", 24000)));
                HttpRequest request = HttpRequest.newBuilder(URI.create(synthesizeUrl)).timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json").header("X-Goog-Api-Key", googleKey)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) throw new IOException("Dịch vụ TTS trả HTTP " + response.statusCode() + ". Kiểm tra cấu hình/quota và thử lại.");
                String encoded = mapper.readTree(response.body()).path("audioContent").asText("");
                if (encoded.isBlank()) throw new IOException("Dịch vụ TTS không trả audioContent.");
                try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
                    AudioFormat current = audio.getFormat();
                    if (!AudioFormat.Encoding.PCM_SIGNED.equals(current.getEncoding()) || current.getSampleSizeInBits() != 16 || current.isBigEndian()
                            || (format != null && !format.matches(current))) throw new IOException("Các đoạn audio không cùng định dạng WAV.");
                    format = current;
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = audio.read(buffer)) != -1) { output.write(buffer, 0, count); length += count; }
                }
            }
            if (format == null || length == 0 || length > Integer.MAX_VALUE - 44) throw new IOException("Độ dài audio không hợp lệ.");
            output.seek(0); output.writeBytes("RIFF"); little32(output, (int) length + 36); output.writeBytes("WAVEfmt "); little32(output, 16);
            little16(output, 1); little16(output, format.getChannels()); little32(output, (int) format.getSampleRate());
            little32(output, (int) format.getSampleRate() * format.getFrameSize()); little16(output, format.getFrameSize()); little16(output, 16);
            output.writeBytes("data"); little32(output, (int) length);
        }
    }

    private void piper(String text, Path temporary) throws Exception {
        Path input = Files.createTempFile(storage, "piper-input-", ".txt");
        Process process = null;
        try {
            Files.writeString(input, text + System.lineSeparator(), StandardCharsets.UTF_8);
            process = new ProcessBuilder(piperExecutable, "--model", piperModel, "--output_file", temporary.toString())
                    .redirectInput(input.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(90, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IOException("Piper không tạo được audio. Kiểm tra model/ngôn ngữ rồi thử lại.");
        } finally {
            if (process != null && process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
            Files.deleteIfExists(input);
        }
    }
    private static void little16(RandomAccessFile out, int value) throws IOException { out.writeShort(Short.reverseBytes((short) value)); }
    private static void little32(RandomAccessFile out, int value) throws IOException { out.writeInt(Integer.reverseBytes(value)); }
}
