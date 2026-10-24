package com.voiceover.backend.repositories;

import com.voiceover.backend.entities.AiDescriptionDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiDescriptionDraftRepository extends JpaRepository<AiDescriptionDraft, Long> {
    Optional<AiDescriptionDraft> findFirstByPoiIdOrderByCreatedAtDesc(Long poiId);
    Optional<AiDescriptionDraft> findByIdAndPoiId(Long id, Long poiId);
}
