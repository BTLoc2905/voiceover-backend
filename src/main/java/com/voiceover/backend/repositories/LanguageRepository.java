package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface LanguageRepository extends JpaRepository<Language, String> {
    List<Language> findByIsActiveTrue();
    Optional<Language> findByIsDefaultTrue();
}
