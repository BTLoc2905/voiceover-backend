package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RouteRepository extends JpaRepository<Route, Long> {
    List<Route> findBySessionId(Long sessionId);
    List<Route> findByStatus(String status);
}
