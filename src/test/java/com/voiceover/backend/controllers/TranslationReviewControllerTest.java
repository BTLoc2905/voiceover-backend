package com.voiceover.backend.controllers;

import com.voiceover.backend.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TranslationReviewControllerTest {
    final TranslationReviewService service=mock(TranslationReviewService.class); MockMvc mvc;
    @BeforeEach void setup(){mvc=MockMvcBuilders.standaloneSetup(new TranslationReviewController(service)).setControllerAdvice(new RouteErrorHandler()).build();}
    @Test void saveAndRegenerateReturnAcceptedAndKeepTheirContractsSeparate() throws Exception {
        mvc.perform(put("/api/admin/pois/1/translations/vi").contentType("application/json").content("{\"content\":\"Edited\",\"revision\":\"v1\"}"))
                .andExpect(status().isAccepted()); verify(service).submit(1L,"vi","Edited","v1",false);
        mvc.perform(post("/api/admin/pois/1/translations/vi/audio").contentType("application/json").content("{\"revision\":\"v2\"}"))
                .andExpect(status().isAccepted()); verify(service).submit(1L,"vi",null,"v2",true);
    }
    @Test void staleAndBusyWritesAre409AndValidationIs400() throws Exception {
        when(service.submit(1L,"vi","Edited","v1",false)).thenThrow(new ContentReviewConflictException("Bản dịch đã thay đổi"));
        mvc.perform(put("/api/admin/pois/1/translations/vi").contentType("application/json").content("{\"content\":\"Edited\",\"revision\":\"v1\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTENT_CONFLICT"));
        when(service.submit(1L,"vi","","v1",false)).thenThrow(new IllegalArgumentException("Không được để trống"));
        mvc.perform(put("/api/admin/pois/1/translations/vi").contentType("application/json").content("{\"content\":\"\",\"revision\":\"v1\"}"))
                .andExpect(status().isBadRequest());
    }
}
