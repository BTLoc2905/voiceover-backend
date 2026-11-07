package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.AdminAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {
    Optional<AdminAccount> findByEmail(String email);
}
