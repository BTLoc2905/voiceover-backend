package com.voiceover.backend.config;

import com.voiceover.backend.entities.AdminAccount;
import com.voiceover.backend.repositories.AdminAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSessionInterceptorTest {
    private final AdminAccountRepository accounts = mock(AdminAccountRepository.class);
    private final AdminSessionInterceptor interceptor = new AdminSessionInterceptor(accounts);

    @Test void dashboardRejectsMissingOrDisabledSession() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/admin/monitoring/dashboard");
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        request.getSession().setAttribute("adminId", 1L);
        request.getSession().setAttribute("adminRole", "ADMIN");
        response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test void activeAdminCanReadDashboard() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/admin/monitoring/dashboard");
        request.getSession().setAttribute("adminId", 1L);
        request.getSession().setAttribute("adminRole", "ADMIN");
        when(accounts.findById(1L)).thenReturn(Optional.of(AdminAccount.builder().id(1L).status("ACTIVE").role("ADMIN").build()));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test void expiredPasswordBlocksAllAdminWorkButAllowsChangeMeAndLogout() throws Exception {
        var account = AdminAccount.builder().id(1L).passwordChangedAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()).minusHours(25)).build();
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        for (String path : java.util.List.of("/api/admin/pois", "/api/admin/routes", "/api/admin/monitoring/dashboard")) {
            var request = adminRequest(path);
            var response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
        }
        for (String path : java.util.List.of("/api/admin/auth/me", "/api/admin/auth/password", "/api/admin/auth/logout")) {
            assertTrue(interceptor.preHandle(adminRequest(path), new MockHttpServletResponse(), new Object()));
        }
    }

    @Test void changedCredentialInvalidatesOtherSession() throws Exception {
        when(accounts.findById(1L)).thenReturn(Optional.of(AdminAccount.builder().id(1L).passwordHash("new-hash").build()));
        var request = adminRequest("/api/admin/pois");
        request.getSession().setAttribute("adminCredential", "old-hash");
        var response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest adminRequest(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.getSession().setAttribute("adminId", 1L);
        request.getSession().setAttribute("adminRole", "ADMIN");
        return request;
    }
}
