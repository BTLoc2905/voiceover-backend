package com.voiceover.backend.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiDescriptionRemovalTest {
    @Test void realControllerDiscoveryContainsManualPoiApiButNoAiDescriptionEndpoints() throws Exception {
        // Discover the actual compiled controllers, so a restored/stale AI controller fails this regression.
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Object[] controllers = scanner.findCandidateComponents("com.voiceover.backend.controllers").stream()
                .map(candidate -> {
                    try { return mock(Class.forName(candidate.getBeanClassName())); }
                    catch (ClassNotFoundException error) { throw new IllegalStateException(error); }
                }).toArray();
        assertTrue(controllers.length > 0);
        var mvc = MockMvcBuilders.standaloneSetup(controllers).build();
        var mapping = mvc.getDispatcherServlet().getWebApplicationContext().getBean(RequestMappingHandlerMapping.class);
        String activePaths = mapping.getHandlerMethods().keySet().toString();
        assertTrue(activePaths.contains("/api/admin/pois"));
        assertTrue(activePaths.contains("/api/admin/routes"));
        assertFalse(activePaths.contains("ai-description"));
        String base = "/api/admin/pois/1/ai-description";
        mvc.perform(post(base).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(base + "/latest")).andExpect(status().isNotFound());
        mvc.perform(put(base + "/drafts/1").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(base + "/drafts/1/accept")).andExpect(status().isNotFound());
        mvc.perform(post(base + "/drafts/1/discard")).andExpect(status().isNotFound());
    }
}
