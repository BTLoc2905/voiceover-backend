package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.sound.sampled.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AudioGenerationServiceTest {
    @TempDir Path storage;
    @Test void chunksKeepAllUnicodeAndNeverSplitBeyondProviderLimit() {
        String text="Tiếng Việt 🙂 日本語. ".repeat(1200)+"Cuối cùng";
        var chunks=AudioGenerationService.chunks(text); assertTrue(chunks.size()>2); assertEquals(text,String.join("",chunks));
        for(String chunk:chunks) { assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length<=4500); assertFalse(chunk.isEmpty()); }
    }
    @Test void fullTextProducesOneValidWavAndANewUrlOnEveryGeneration() throws Exception {
        List<String> received=new CopyOnWriteArrayList<>(), voiceNames=new CopyOnWriteArrayList<>(), locales=new CopyOnWriteArrayList<>();
        ObjectMapper mapper=new ObjectMapper();
        byte[] pcm=new byte[48]; ByteArrayOutputStream output=new ByteArrayOutputStream();
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm),new AudioFormat(24000,16,1,true,false),24),AudioFileFormat.Type.WAVE,output);
        String encoded=Base64.getEncoder().encodeToString(output.toByteArray());
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/synthesize",exchange->{
            JsonNode request=mapper.readTree(exchange.getRequestBody());
            received.add(request.path("input").path("text").asText());
            voiceNames.add(request.path("voice").path("name").asText());
            locales.add(request.path("voice").path("languageCode").asText());
            assertNull(exchange.getRequestURI().getQuery()); assertEquals("test-key",exchange.getRequestHeaders().getFirst("X-Goog-Api-Key"));
            byte[] body=mapper.writeValueAsBytes(Map.of("audioContent",encoded)); exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        try {
            var generator=new AudioGenerationService("test-key","google-standard","","",90,storage.toString(),"http://127.0.0.1:"+server.getAddress().getPort()+"/synthesize");
            String text="Nội dung đầy đủ vượt 200 ký tự. ".repeat(180);
            var result=generator.generate(1L,"vi",text); int parts=received.size();
            assertEquals(text,String.join("",received)); assertTrue(parts>1);
            assertTrue(voiceNames.stream().allMatch("vi-VN-Standard-A"::equals));
            assertTrue(locales.stream().allMatch("vi-VN"::equals));
            Path file=storage.resolve(result.url().substring(result.url().lastIndexOf('/')+1));
            try(AudioInputStream audio=AudioSystem.getAudioInputStream(file.toFile())) { assertEquals(24L*parts,audio.getFrameLength()); }
            var second=generator.generate(1L,"vi",text); assertNotEquals(result.url(),second.url());
            generator.generate(1L,"en","English example");
            assertEquals("en-US-Standard-A",voiceNames.get(voiceNames.size()-1));
            assertEquals("en-US",locales.get(locales.size()-1));
            try(var files=Files.list(storage)){assertEquals(3,files.count());}
        } finally { server.stop(0); }
    }
    @Test void unconfiguredProviderDoesNotCreateAFakeFile() {
        var generator=new AudioGenerationService("","google-standard","","",90,storage.toString(),"https://unused.example");
        assertThrows(IllegalStateException.class,()->generator.generate(1L,"vi","Text"));
        assertEquals("UNAVAILABLE",generator.provider("vi"));
    }

    @Test void unknownLanguageNeverFallsBackToAnotherVoice() throws Exception {
        var generator=new AudioGenerationService("test-key","google-standard","","",90,storage.toString(),"https://unused.example");
        assertFalse(generator.supportsStandardVoice("xx"));
        assertEquals("UNSUPPORTED",generator.provider("xx"));
        assertThrows(IllegalArgumentException.class,()->generator.generate(1L,"xx","Text"));
        try (var files = Files.list(storage)) { assertEquals(0, files.count()); }
    }

    @Test void edgeSelectionDoesNotNeedGoogleKeyOrStandardVoice() {
        var generator=new AudioGenerationService("","edge","python","scripts/edge_tts_worker.py",90,storage.toString(),"https://unused.example");
        assertEquals("Edge-TTS",generator.provider("vi"));
        assertEquals("Edge-TTS",generator.provider("en"));
        assertTrue(generator.supportsLanguage("vi"));
        assertTrue(generator.supportsLanguage("en"));
        assertEquals("UNSUPPORTED",generator.provider("xx"));
    }

    @Test void storedAudioReadinessRequiresARealFileInsideAudioStorage() throws Exception {
        var generator=new AudioGenerationService("","edge","","",90,storage.toString(),"https://unused.example");
        Path file=storage.resolve("existing.wav");
        byte[] pcm=new byte[48];
        try(OutputStream output=Files.newOutputStream(file)) {
            AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm),new AudioFormat(24000,16,1,true,false),24),AudioFileFormat.Type.WAVE,output);
        }
        assertTrue(generator.hasUsableFile("/storage/audio/existing.wav"));
        assertTrue(generator.hasUsableFile("/audio/existing.wav"));
        assertFalse(generator.hasUsableFile("/storage/audio/missing.wav"));
        assertFalse(generator.hasUsableFile("/storage/audio/../../outside.wav"));
        assertFalse(generator.hasUsableFile("https://example.test/existing.wav"));
    }
}
