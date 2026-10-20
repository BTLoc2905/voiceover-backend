package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {
    List<RouteStop> findByRouteIdOrderByStopOrderAsc(Long routeId);
    void deleteByRouteId(Long routeId);

    @Query("SELECT COUNT(rs) > 0 FROM RouteStop rs JOIN Route r ON rs.routeId = r.id JOIN Session s ON r.sessionId = s.id WHERE rs.poiId = :poiId AND (r.status = 'PUBLISHED' OR s.status = 'PUBLISHED')")
    boolean existsInPublishedRouteOrSession(@Param("poiId") Long poiId);
}
