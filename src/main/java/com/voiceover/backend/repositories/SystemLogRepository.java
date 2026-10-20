package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    List<SystemLog> findTop20ByOrderByOccurredAtDesc();
}
