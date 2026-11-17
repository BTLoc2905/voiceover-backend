package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByEmail(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AdminAccount a where a.id = :id")
    Optional<AdminAccount> findForPasswordChange(@org.springframework.data.repository.query.Param("id") Long id);
}
