package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {
    List<RouteStop> findByRouteIdOrderByStopOrderAsc(Long routeId);
    void deleteByRouteId(Long routeId);

    @Query("SELECT DISTINCT r FROM RouteStop rs JOIN Route r ON rs.routeId = r.id JOIN Session s ON r.sessionId = s.id WHERE rs.poiId = :poiId AND UPPER(r.status) = 'PUBLISHED' AND UPPER(s.status) = 'PUBLISHED'")
    List<com.voiceover.backend.entities.Route> findServingRoutes(@Param("poiId") Long poiId);

    @Query("SELECT DISTINCT r FROM RouteStop rs JOIN Route r ON rs.routeId = r.id WHERE rs.poiId = :poiId")
    List<com.voiceover.backend.entities.Route> findReferencingRoutes(@Param("poiId") Long poiId);
}
