package com.voiceover.backend.services;

import com.voiceover.backend.entities.AdminAccount;
import com.voiceover.backend.repositories.AdminAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminPasswordService {
    private final AdminAccountRepository accounts;
    private final PasswordEncoder encoder;

    @Transactional
    public AdminAccount change(Long id, String currentPassword, String newPassword, String confirmation) {
        var account = accounts.findForPasswordChange(id).filter(value -> "ACTIVE".equalsIgnoreCase(value.getStatus())
                && "ADMIN".equalsIgnoreCase(value.getRole())).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
        if (currentPassword == null || !encoder.matches(currentPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 8 ký tự và tối đa 72 byte.");
        }
        if (!Objects.equals(newPassword, confirmation)) {
            throw new IllegalArgumentException("Xác nhận mật khẩu không khớp.");
        }
        if (encoder.matches(newPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        account.setPasswordHash(encoder.encode(newPassword));
        account.setPasswordChangedAt(LocalDateTime.now(Clock.systemUTC()));
        return accounts.save(account);
    }
}
