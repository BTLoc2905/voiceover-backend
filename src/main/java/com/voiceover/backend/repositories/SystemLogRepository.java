package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDateTime;
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    List<SystemLog> findTop20ByOrderByOccurredAtDesc();
    List<SystemLog> findTop10BySeverityInAndOccurredAtAfterOrderByOccurredAtDesc(List<String> severities, LocalDateTime since);
}
