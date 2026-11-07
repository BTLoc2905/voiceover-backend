package com.voiceover.backend.controllers;

import com.voiceover.backend.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/pois/{poiId}/translations")
@RequiredArgsConstructor
public class TranslationReviewController {
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<?> databaseUnavailable() {
        return ResponseEntity.status(503).body(java.util.Map.of("code", "CONTENT_STORAGE_ERROR", "message", "Không đọc hoặc lưu được nội dung lúc này. Hãy giữ bản đang soạn và thử lại."));
    }
    private final TranslationReviewService service;
    public record EditInput(String content, String revision) {}
    public record RegenerateInput(String revision) {}
    @GetMapping public Object index(@PathVariable Long poiId) { return service.index(poiId); }
    @GetMapping("/{code}") public Object detail(@PathVariable Long poiId, @PathVariable String code) { return service.detail(poiId, code); }
    @PutMapping("/{code}") public ResponseEntity<?> save(@PathVariable Long poiId, @PathVariable String code, @RequestBody EditInput input) {
        return ResponseEntity.accepted().body(service.submit(poiId, code, input.content(), input.revision(), false));
    }
    @PostMapping("/{code}/audio") public ResponseEntity<?> regenerate(@PathVariable Long poiId, @PathVariable String code, @RequestBody RegenerateInput input) {
        return ResponseEntity.accepted().body(service.submit(poiId, code, null, input.revision(), true));
    }
}
