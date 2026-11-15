package com.voiceover.backend.controllers;

import com.voiceover.backend.config.AdminSessionInterceptor;
import com.voiceover.backend.entities.AdminAccount;
import com.voiceover.backend.repositories.AdminAccountRepository;
import com.voiceover.backend.services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RouteAdminAuthenticationTest {
    @Mock AdminAccountRepository accounts;
    @Mock MonitoringService monitoring;
    @Mock RouteService routes;
    @Spy BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @InjectMocks VoiceMapController controller;

    @Test void adminLoginKeepsHttpSessionForRoutesAndDashboard() throws Exception {
        var account = AdminAccount.builder().id(7L).email("route-test@example.test").fullName("Route tester")
                .role("ADMIN").status("ACTIVE").passwordHash(passwordEncoder.encode("test-password")).build();
        when(accounts.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(routes.listRoutes()).thenReturn(List.of(Map.of("id", 1L)));
        when(monitoring.dashboard()).thenReturn(Map.of("status", "UP"));
        var mvc = MockMvcBuilders.standaloneSetup(controller, new RoutesController(routes))
                .addInterceptors(new AdminSessionInterceptor(accounts)).setControllerAdvice(new RouteErrorHandler()).build();
        mvc.perform(get("/api/admin/routes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/monitoring/dashboard")).andExpect(status().isUnauthorized());
        var login = mvc.perform(post("/api/admin/auth/login").contentType("application/json")
                .content("{\"email\":\"route-test@example.test\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session); assertEquals(7L, session.getAttribute("adminId"));
        mvc.perform(get("/api/admin/routes").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/monitoring/dashboard").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/auth/logout").session(session)).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
    }
}
