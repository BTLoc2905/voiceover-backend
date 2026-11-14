package com.voiceover.backend.controllers;

import com.voiceover.backend.config.AdminSessionInterceptor;
import com.voiceover.backend.entities.AdminAccount;
import com.voiceover.backend.repositories.AdminAccountRepository;
import com.voiceover.backend.services.MonitoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MonitoringAccessTest {
    @Mock AdminAccountRepository accounts;
    @Mock MonitoringService monitoring;
    @InjectMocks VoiceMapController controller;

    MockHttpSession session(String role) {
        var session = new MockHttpSession(); session.setAttribute("adminRole", role); session.setAttribute("adminId", 7L); return session;
    }

    @Test void anonymousNonAdminAndDisabledAccountsCannotReadMonitoring() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new AdminSessionInterceptor(accounts)).build();
        mvc.perform(get("/api/admin/monitoring/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/monitoring/dashboard").session(session("GUEST"))).andExpect(status().isUnauthorized());
        when(accounts.findById(7L)).thenReturn(Optional.of(AdminAccount.builder().id(7L).status("INACTIVE").role("ADMIN").build()));
        mvc.perform(get("/api/admin/monitoring/dashboard").session(session("ADMIN"))).andExpect(status().isUnauthorized());
        when(accounts.findById(7L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/admin/monitoring/dashboard").session(session("ADMIN"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(monitoring);
    }

    @Test void authenticatedAdminGetsActualSnapshotWithoutBrowserCaching() throws Exception {
        when(accounts.findById(7L)).thenReturn(Optional.of(AdminAccount.builder().id(7L).status("ACTIVE").role("ADMIN").build()));
        when(monitoring.dashboard()).thenReturn(Map.of("activeUsers", 3, "pipelineCompleted", 8));
        var mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new AdminSessionInterceptor(accounts)).build();
        mvc.perform(get("/api/admin/monitoring/dashboard").session(session("ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeUsers").value(3)).andExpect(jsonPath("$.pipelineCompleted").value(8))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
