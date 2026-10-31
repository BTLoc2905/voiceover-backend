package com.voiceover.backend.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceover.backend.entities.Poi;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Canonical JSON avoids delimiter collisions; term ordering must not change content identity. */
final class ContentFingerprint {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ContentFingerprint() {}

    static List<String> terms(Collection<String> values) {
        return values.stream().filter(Objects::nonNull).filter(term -> !term.isBlank())
                .distinct().sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder())).toList();
    }

    static String source(Poi poi, List<String> terms) {
        return hash(json(List.of("source-v1", poi.getDescriptionSource(), poi.getSourceLanguageCode(), terms(terms))));
    }

    static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("Không mã hóa được phiên bản nội dung.", error); }
    }

    static List<String> readTerms(String value) {
        try { return JSON.readValue(value, new TypeReference<List<String>>() {}); }
        catch (Exception error) { throw new IllegalStateException("Tác vụ thiếu danh sách thuật ngữ hợp lệ.", error); }
    }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("Không tính được phiên bản nội dung.", error); }
    }
}
