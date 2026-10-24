package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.Poi;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface PoiRepository extends JpaRepository<Poi, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Poi p where p.id = :id")
    java.util.Optional<Poi> findForUpdate(@Param("id") Long id);
    List<Poi> findByStatus(String status);
    java.util.Optional<Poi> findByName(String name);
}
