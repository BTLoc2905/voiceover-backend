package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.Audio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface AudioRepository extends JpaRepository<Audio, Long> {
    List<Audio> findByPoiId(Long poiId);
    Optional<Audio> findByPoiIdAndLanguageCode(Long poiId, String languageCode);
}
