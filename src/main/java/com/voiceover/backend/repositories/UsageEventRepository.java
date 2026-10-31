package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;
public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {
    long countByEventType(String eventType);

    boolean existsByAnonClientIdAndPlaybackIdAndEventType(String anonClientId, String playbackId, String eventType);

    @Query("select count(distinct u.anonClientId) from UsageEvent u where u.anonClientId is not null and u.anonClientId <> ''")
    long countDistinctUsers();

    @Query("select u.poiId, count(u) from UsageEvent u where u.poiId is not null and u.eventType in ('AUTO_PLAY', 'TAP_POI') group by u.poiId")
    List<Object[]> countListensByPoi();

    @Query("select count(distinct u.anonClientId) from UsageEvent u where u.anonClientId is not null and u.anonClientId <> '' and u.occurredAt >= :since")
    long countActiveUsers(@Param("since") LocalDateTime since);

    @Query("select u.poiId, count(u) from UsageEvent u where u.poiId is not null and u.eventType in ('AUTO_PLAY', 'TAP_POI') and u.occurredAt >= :since group by u.poiId")
    List<Object[]> countRecentListensByPoi(@Param("since") LocalDateTime since);

    @Query("select u.languageCode, count(u) from UsageEvent u where u.languageCode is not null and u.eventType in ('AUTO_PLAY', 'TAP_POI') and u.occurredAt >= :since group by u.languageCode order by count(u) desc")
    List<Object[]> countRecentListensByLanguage(@Param("since") LocalDateTime since);

    @Query("select u.eventType, count(u) from UsageEvent u where u.eventType in ('AUTO_PLAY', 'TAP_POI') and u.occurredAt >= :since group by u.eventType")
    List<Object[]> countRecentListensByTrigger(@Param("since") LocalDateTime since);

    @Query("select u.routeId, count(u) from UsageEvent u where u.eventType = 'ROUTE_SELECTED' and u.routeId is not null and u.occurredAt >= :since group by u.routeId order by count(u) desc")
    List<Object[]> countRecentRouteSelections(@Param("since") LocalDateTime since);
}
