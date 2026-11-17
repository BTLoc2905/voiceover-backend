package com.voiceover.backend.services;

import com.voiceover.backend.entities.AdminAccount;
import com.voiceover.backend.repositories.AdminAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPasswordServiceTest {
    final AdminAccountRepository accounts = mock(AdminAccountRepository.class);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    final AdminPasswordService service = new AdminPasswordService(accounts, encoder);
    final AdminAccount account = AdminAccount.builder().id(1L).passwordHash(encoder.encode("old-password"))
            .passwordChangedAt(LocalDateTime.of(2026, 11, 16, 0, 0)).build();

    @Test void expiresAtExactly24HoursAndNeverRefreshesOnLogin() {
        assertFalse(AdminPasswordPolicy.expired(account, Instant.parse("2026-11-16T23:59:59Z")));
        assertTrue(AdminPasswordPolicy.expired(account, Instant.parse("2026-11-17T00:00:00Z")));
        var before = account.getPasswordChangedAt();
        var view = AdminPasswordPolicy.accountView(account);
        assertEquals("2026-11-17T00:00:00Z", view.get("passwordExpiresAt"));
        assertFalse(view.containsKey("passwordHash"));
        assertEquals(before, account.getPasswordChangedAt());
        account.setPasswordChangedAt(null);
        assertTrue(AdminPasswordPolicy.expired(account, Instant.now()));
    }

    @Test void wrongCurrentWeakSameOrMismatchedPasswordNeverSaves() {
        when(accounts.findForPasswordChange(1L)).thenReturn(Optional.of(account));
        for (String[] values : new String[][] {
            {"wrong", "new-password", "new-password"}, {"old-password", "short", "short"},
            {"old-password", "old-password", "old-password"}, {"old-password", "new-password", "different"},
            {"old-password", "ắ".repeat(25), "ắ".repeat(25)}
        }) assertThrows(IllegalArgumentException.class, () -> service.change(1L, values[0], values[1], values[2]));
        verify(accounts, never()).save(any());
    }

    @Test void validChangeHashesPasswordAndResetsExpiry() {
        when(accounts.findForPasswordChange(1L)).thenReturn(Optional.of(account));
        when(accounts.save(account)).thenReturn(account);
        var changed = service.change(1L, "old-password", "new-password", "new-password");
        assertTrue(encoder.matches("new-password", changed.getPasswordHash()));
        assertFalse(encoder.matches("old-password", changed.getPasswordHash()));
        assertFalse(AdminPasswordPolicy.expired(changed, Instant.now()));
        verify(accounts).save(account);
    }

    @Test void inactiveAccountCannotChangePassword() {
        account.setStatus("LOCKED");
        when(accounts.findForPasswordChange(1L)).thenReturn(Optional.of(account));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.change(1L, "old-password", "new-password", "new-password"));
        verify(accounts, never()).save(any());
    }
}
