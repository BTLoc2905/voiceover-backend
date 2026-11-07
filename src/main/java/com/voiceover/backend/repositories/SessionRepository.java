package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface SessionRepository extends JpaRepository<Session, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.id = :id")
    java.util.Optional<Session> findForUpdate(@Param("id") Long id);
    List<Session> findByStatus(String status);
}
