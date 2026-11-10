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
}
