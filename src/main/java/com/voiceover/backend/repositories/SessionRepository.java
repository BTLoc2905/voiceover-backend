package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByStatus(String status);
}
