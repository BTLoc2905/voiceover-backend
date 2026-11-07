package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface TranslationRepository extends JpaRepository<Translation, Long> {
    List<Translation> findByPoiId(Long poiId);
    Optional<Translation> findByPoiIdAndLanguageCode(Long poiId, String languageCode);
}
