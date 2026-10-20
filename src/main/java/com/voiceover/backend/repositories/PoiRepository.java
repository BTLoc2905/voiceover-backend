package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.Poi;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PoiRepository extends JpaRepository<Poi, Long> {
    List<Poi> findByStatus(String status);
    java.util.Optional<Poi> findByName(String name);
}
