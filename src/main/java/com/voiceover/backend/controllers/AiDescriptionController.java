package com.voiceover.backend.controllers;

import com.voiceover.backend.services.AiDescriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/pois/{poiId}/ai-description")
@RequiredArgsConstructor
public class AiDescriptionController {
    private final AiDescriptionService service;
    public record GenerateInput(String notes) {}
    public record EditInput(String content) {}

    @GetMapping("/latest")
    public Object latest(@PathVariable Long poiId) { return service.latest(poiId); }

    @PostMapping
    public Object generate(@PathVariable Long poiId, @RequestBody(required = false) GenerateInput input) {
        return service.generate(poiId, input == null ? "" : input.notes());
    }

    @PutMapping("/drafts/{draftId}")
    public Object edit(@PathVariable Long poiId, @PathVariable Long draftId, @RequestBody EditInput input) {
        return service.edit(poiId, draftId, input.content());
    }

    @PostMapping("/drafts/{draftId}/accept")
    public Object accept(@PathVariable Long poiId, @PathVariable Long draftId) { return service.accept(poiId, draftId); }

    @PostMapping("/drafts/{draftId}/discard")
    public Object discard(@PathVariable Long poiId, @PathVariable Long draftId) { return service.discard(poiId, draftId); }
}
