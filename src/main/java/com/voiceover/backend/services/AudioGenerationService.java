package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
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
    private final String googleKey, synthesizeUrl, ttsProvider, edgePython, edgeWorker;
    private final int edgeTimeoutSeconds;
    private final Path storage;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    public record GeneratedAudio(String url, int durationSec) {}
    private record StandardVoice(String locale, String name) {}
    // Explicit Standard names from https://cloud.google.com/text-to-speech/docs/list-voices-and-types.
    // Unknown languages are unsupported; never let Google pick a premium voice implicitly.
    private static final Map<String, StandardVoice> STANDARD_VOICES = Map.ofEntries(
            Map.entry("vi", new StandardVoice("vi-VN", "vi-VN-Standard-A")),
            Map.entry("en", new StandardVoice("en-US", "en-US-Standard-A")),
            Map.entry("fr", new StandardVoice("fr-FR", "fr-FR-Standard-F")),
            Map.entry("de", new StandardVoice("de-DE", "de-DE-Standard-G")),
            Map.entry("es", new StandardVoice("es-ES", "es-ES-Standard-F")),
            Map.entry("it", new StandardVoice("it-IT", "it-IT-Standard-E")),
            Map.entry("ja", new StandardVoice("ja-JP", "ja-JP-Standard-A")),
            Map.entry("ko", new StandardVoice("ko-KR", "ko-KR-Standard-A")),
            Map.entry("zh-CN", new StandardVoice("cmn-CN", "cmn-CN-Standard-A")),
            Map.entry("zh-TW", new StandardVoice("cmn-TW", "cmn-TW-Standard-A")),
            Map.entry("th", new StandardVoice("th-TH", "th-TH-Standard-A")),
            Map.entry("ru", new StandardVoice("ru-RU", "ru-RU-Standard-A")),
            Map.entry("id", new StandardVoice("id-ID", "id-ID-Standard-A")),
            Map.entry("pt", new StandardVoice("pt-BR", "pt-BR-Standard-A")),
            Map.entry("ar", new StandardVoice("ar-XA", "ar-XA-Standard-A")),
            Map.entry("ms", new StandardVoice("ms-MY", "ms-MY-Standard-A")),
            Map.entry("hi", new StandardVoice("hi-IN", "hi-IN-Standard-A")));
    // Verified against edge-tts --list-voices on 26/10/2026. An unavailable voice fails;
    // the service never silently selects a different language or provider.
    private static final Map<String, String> EDGE_VOICES = Map.ofEntries(
            Map.entry("vi", "vi-VN-HoaiMyNeural"), Map.entry("en", "en-US-JennyNeural"),
            Map.entry("ja", "ja-JP-NanamiNeural"), Map.entry("ko", "ko-KR-SunHiNeural"),
            Map.entry("zh-CN", "zh-CN-XiaoxiaoNeural"), Map.entry("fr", "fr-FR-DeniseNeural"),
            Map.entry("de", "de-DE-KatjaNeural"), Map.entry("es", "es-ES-ElviraNeural"),
            Map.entry("ru", "ru-RU-SvetlanaNeural"), Map.entry("th", "th-TH-PremwadeeNeural"),
            Map.entry("id", "id-ID-GadisNeural"), Map.entry("it", "it-IT-IsabellaNeural"),
            Map.entry("pt", "pt-BR-FranciscaNeural"), Map.entry("ar", "ar-SA-ZariyahNeural"),
            Map.entry("ms", "ms-MY-YasminNeural"), Map.entry("hi", "hi-IN-SwaraNeural"));

    public AudioGenerationService(@Value("${google.cloud.api.key:}") String googleKey,
            @Value("${app.tts.provider:edge}") String ttsProvider,
            @Value("${app.tts.edge.python:.venv-edge-tts/Scripts/python.exe}") String edgePython,
            @Value("${app.tts.edge.worker:scripts/edge_tts_worker.py}") String edgeWorker,
            @Value("${app.tts.edge.timeout-seconds:90}") int edgeTimeoutSeconds,
            @Value("${app.audio.storage:storage/audio}") String storage,
            @Value("${app.tts.synthesize-url:https://texttospeech.googleapis.com/v1/text:synthesize}") String synthesizeUrl) {
        if (!Set.of("edge", "google-standard").contains(ttsProvider))
            throw new IllegalArgumentException("TTS_PROVIDER phải là edge hoặc google-standard.");
        if (edgeTimeoutSeconds <= 0) throw new IllegalArgumentException("EDGE_TTS_TIMEOUT_SECONDS phải lớn hơn 0.");
        this.googleKey = googleKey; this.ttsProvider = ttsProvider; this.edgePython = edgePython;
        this.edgeWorker = edgeWorker; this.edgeTimeoutSeconds = edgeTimeoutSeconds;
        this.storage = Paths.get(storage).toAbsolutePath().normalize(); this.synthesizeUrl = synthesizeUrl;
    }

    public boolean supportsStandardVoice(String language) { return STANDARD_VOICES.containsKey(language); }
    public boolean supportsLanguage(String language) {
        return "edge".equals(ttsProvider) ? EDGE_VOICES.containsKey(language) : supportsStandardVoice(language);
    }

    public String provider(String language) {
        if (!supportsLanguage(language)) return "UNSUPPORTED";
        if ("edge".equals(ttsProvider)) return "Edge-TTS";
        if (!googleKey.isBlank()) return "Google Cloud TTS";
        return "UNAVAILABLE";
    }

    public boolean hasUsableFile(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            String path = URI.create(url).getPath();
            String prefix;
            if (path != null && path.startsWith("/storage/audio/")) prefix = "/storage/audio/";
            else if (path != null && path.startsWith("/audio/")) prefix = "/audio/";
            else return false;
            Path file = storage.resolve(path.substring(prefix.length())).normalize();
            if (!file.startsWith(storage) || !Files.isRegularFile(file)) return false;
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".mp3") && Files.size(file) > 128) {
                try (InputStream stream = Files.newInputStream(file)) {
                    byte[] head = stream.readNBytes(3);
                    return head.length == 3 && ((head[0] == 'I' && head[1] == 'D' && head[2] == '3')
                            || ((head[0] & 0xff) == 0xff && (head[1] & 0xe0) == 0xe0));
                }
            }
            if (lower.endsWith(".wav") && Files.size(file) > 44) {
                try (InputStream stream = Files.newInputStream(file)) {
                    byte[] head = stream.readNBytes(12);
                    return head.length == 12 && new String(head, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                            && new String(head, 8, 4, StandardCharsets.US_ASCII).equals("WAVE");
                }
            }
            return false;
        } catch (IllegalArgumentException | IOException | SecurityException error) {
            return false;
        }
    }

    public GeneratedAudio generate(Long poiId, String language, String text) throws Exception {
        if (!language.matches("[A-Za-z0-9-]{1,10}")) throw new IllegalArgumentException("Mã ngôn ngữ không hợp lệ.");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Bản dịch chưa có nội dung.");
        if (!supportsLanguage(language)) throw new IllegalArgumentException("Ngôn ngữ này không có giọng của TTS provider đang chọn.");
        if ("google-standard".equals(ttsProvider) && googleKey.isBlank())
            throw new IllegalStateException("Chưa cấu hình GOOGLE_CLOUD_API_KEY cho Google Standard TTS.");
        Files.createDirectories(storage);
        String name = "poi_" + poiId + "_" + language + "_" + UUID.randomUUID()
                + ("edge".equals(ttsProvider) ? ".mp3" : ".wav");
        Path destination = storage.resolve(name), temporary = storage.resolve(name + ".partial");
        try {
            int duration;
            if ("edge".equals(ttsProvider)) duration = edge(text, EDGE_VOICES.get(language), temporary);
            else {
                google(text, language, temporary);
                try (AudioInputStream audio = AudioSystem.getAudioInputStream(temporary.toFile())) {
                    if (audio.getFrameLength() <= 0) throw new IOException("Dịch vụ không tạo được nội dung audio hợp lệ.");
                    duration = Math.max(1, (int) Math.ceil(audio.getFrameLength() / audio.getFormat().getFrameRate()));
                }
            }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, destination); }
            return new GeneratedAudio("/storage/audio/" + name, duration);
        } finally { Files.deleteIfExists(temporary); }
    }

    /** Only a newly generated, rejected result may be discarded; never accepts arbitrary paths. */
    public void discard(GeneratedAudio generated) throws IOException {
        if (!generated.url().startsWith("/storage/audio/")) throw new IllegalArgumentException("Đường dẫn audio không hợp lệ.");
        String name = generated.url().substring("/storage/audio/".length());
        if (!name.matches("poi_[0-9]+_[A-Za-z0-9-]+_[a-f0-9-]+\\.(wav|mp3)"))
            throw new IllegalArgumentException("Đường dẫn audio không hợp lệ.");
        Path file = storage.resolve(name).normalize();
        if (!file.startsWith(storage)) throw new IllegalArgumentException("Đường dẫn audio không hợp lệ.");
        Files.deleteIfExists(file);
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

    private int edge(String text, String voice, Path temporary) throws Exception {
        Path worker = Paths.get(edgeWorker).toAbsolutePath().normalize();
        if (!Files.isRegularFile(worker)) throw new IllegalStateException("Không tìm thấy Edge-TTS worker: " + worker);
        String python = edgePython;
        if (python.contains("/") || python.contains("\\")) {
            Path executable = Paths.get(python).toAbsolutePath().normalize();
            if (!Files.isRegularFile(executable))
                throw new IllegalStateException("Không tìm thấy Python Edge-TTS: " + executable
                        + ". Cài requirements-edge-tts.txt hoặc đặt EDGE_TTS_PYTHON.");
            python = executable.toString();
        }
        Path input = Files.createTempFile(storage, "edge-input-", ".txt");
        Process process = null;
        try {
            Files.writeString(input, text, StandardCharsets.UTF_8);
            process = new ProcessBuilder(python, worker.toString(), "--input", input.toString(),
                    "--output", temporary.toString(), "--voice", voice).redirectErrorStream(true).start();
            if (!process.waitFor(edgeTimeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
                throw new IOException("Edge-TTS quá thời gian " + edgeTimeoutSeconds + " giây.");
            }
            String result = new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8).strip();
            if (process.exitValue() != 0) throw new IOException("Edge-TTS thất bại: " + result);
            JsonNode metadata = mapper.readTree(result);
            int duration = metadata.path("durationSec").asInt();
            long bytes = metadata.path("bytes").asLong();
            if (duration <= 0 || bytes <= 128 || !Files.isRegularFile(temporary) || Files.size(temporary) != bytes)
                throw new IOException("Edge-TTS trả metadata MP3 không hợp lệ.");
            return duration;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(input);
        }
    }

    private void google(String text, String language, Path temporary) throws Exception {
        StandardVoice voice = STANDARD_VOICES.get(language);
        // LINEAR16 contains a WAV header. Decode each container and write ONE WAV, not concatenated MP3 headers.
        try (RandomAccessFile output = new RandomAccessFile(temporary.toFile(), "rw")) {
            output.setLength(44); output.seek(44);
            AudioFormat format = null; long length = 0;
            for (String part : chunks(text)) {
                String body = mapper.writeValueAsString(Map.of("input", Map.of("text", part),
                        "voice", Map.of("languageCode", voice.locale(), "name", voice.name()),
                        "audioConfig", Map.of("audioEncoding", "LINEAR16", "sampleRateHertz", 24000)));
                HttpRequest request = HttpRequest.newBuilder(URI.create(synthesizeUrl)).timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json").header("X-Goog-Api-Key", googleKey)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) throw new ProviderRequestException("Dịch vụ TTS", response.statusCode());
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

    private static void little16(RandomAccessFile out, int value) throws IOException { out.writeShort(Short.reverseBytes((short) value)); }
    private static void little32(RandomAccessFile out, int value) throws IOException { out.writeInt(Integer.reverseBytes(value)); }
}
