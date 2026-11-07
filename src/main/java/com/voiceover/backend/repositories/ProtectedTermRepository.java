package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.ProtectedTerm;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProtectedTermRepository extends JpaRepository<ProtectedTerm, Long> {}
