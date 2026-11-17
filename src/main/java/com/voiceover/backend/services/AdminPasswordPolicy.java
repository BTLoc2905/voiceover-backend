package com.voiceover.backend.services;

import com.voiceover.backend.entities.AdminAccount;
import java.time.*;
import java.util.Map;

public final class AdminPasswordPolicy {
    private AdminPasswordPolicy() {}

    public static boolean expired(AdminAccount account, Instant now) {
        return account.getPasswordChangedAt() == null
                || !now.isBefore(account.getPasswordChangedAt().toInstant(ZoneOffset.UTC).plus(Duration.ofHours(24)));
    }

    public static Map<String, Object> accountView(AdminAccount account) {
        var result = new java.util.HashMap<String, Object>();
        result.put("id", account.getId());
        result.put("email", account.getEmail());
        result.put("fullName", account.getFullName());
        result.put("role", account.getRole());
        result.put("passwordChangeRequired", expired(account, Instant.now()));
        if (account.getPasswordChangedAt() != null) {
            result.put("passwordExpiresAt", account.getPasswordChangedAt().toInstant(ZoneOffset.UTC).plus(Duration.ofHours(24)).toString());
        }
        return result;
    }
}
