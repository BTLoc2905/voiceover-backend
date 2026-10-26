package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {
    long countByEventType(String eventType);

    boolean existsByAnonClientIdAndPlaybackIdAndEventType(String anonClientId, String playbackId, String eventType);

    @Query("select count(distinct u.anonClientId) from UsageEvent u where u.anonClientId is not null and u.anonClientId <> ''")
    long countDistinctUsers();

    @Query("select u.poiId, count(u) from UsageEvent u where u.poiId is not null and u.eventType in ('AUTO_PLAY', 'TAP_POI') group by u.poiId")
    List<Object[]> countListensByPoi();
}
