package com.voiceover.backend.controllers;

import com.voiceover.backend.entities.Language;
import com.voiceover.backend.entities.UsageEvent;
import com.voiceover.backend.repositories.LanguageRepository;
import com.voiceover.backend.repositories.PoiRepository;
import com.voiceover.backend.repositories.UsageEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/public/playback-events")
@RequiredArgsConstructor
public class PlaybackEventController {
    private final PoiRepository poiRepo;
    private final LanguageRepository languageRepo;
    private final UsageEventRepository eventRepo;
    private record Key(String client, String playback, String type) {}
    private final Map<Key, Long> recent = new LinkedHashMap<>();
    public record Input(Long poiId, String languageCode, String eventType, String clientId, String playbackId) {}

    // Repository saveAndFlush commits before caching the key. No schema change is required.
    @PostMapping
    public synchronized Map<String, Boolean> record(@RequestBody Input input) {
        if (input.poiId() == null || input.poiId() <= 0 || input.clientId() == null || input.clientId().isBlank() || input.clientId().length() > 64
                || input.playbackId() == null || input.playbackId().isBlank() || input.playbackId().length() > 64
                || input.eventType() == null || !Set.of("AUTO_PLAY", "TAP_POI", "AUDIO_COMPLETED").contains(input.eventType())
                || input.languageCode() == null || input.languageCode().length() > 10) throw new IllegalArgumentException("Sự kiện nghe không hợp lệ.");
        if (poiRepo.findById(input.poiId()).filter(poi -> "PUBLISHED".equalsIgnoreCase(poi.getStatus())).isEmpty()) throw new IllegalArgumentException("POI không còn khả dụng.");
        if (languageRepo.findById(input.languageCode()).filter(Language::isActive).isEmpty()) throw new IllegalArgumentException("Ngôn ngữ không còn hoạt động.");
        long now = System.currentTimeMillis();
        recent.entrySet().removeIf(entry -> now - entry.getValue() > 30 * 60 * 1000);
        Key key = new Key(input.clientId(), input.playbackId(), input.eventType());
        if (recent.containsKey(key)) return Map.of("recorded", false);
        eventRepo.saveAndFlush(UsageEvent.builder().poiId(input.poiId()).languageCode(input.languageCode())
                .eventType(input.eventType()).anonClientId(input.clientId()).build());
        recent.put(key, now);
        if (recent.size() > 2048) recent.remove(recent.keySet().iterator().next());
        return Map.of("recorded", true);
    }
}
