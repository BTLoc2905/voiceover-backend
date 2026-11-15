package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RouteRepository extends JpaRepository<Route, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Route r WHERE r.id = :id")
    java.util.Optional<Route> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<Route> findByStatus(String status);
}
