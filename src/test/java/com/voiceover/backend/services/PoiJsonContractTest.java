package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceover.backend.entities.Poi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PoiJsonContractTest {
    @Test
    void acceptsNewDraftPoiJson() throws Exception {
        String json = """
                {"name":"Edge test","latitude":10.7769,"longitude":106.7009,
                 "radiusM":50,"descriptionSource":"This is a test.",
                 "sourceLanguageCode":"vi","status":"DRAFT"}
                """;
        Poi poi = new ObjectMapper().readValue(json, Poi.class);
        assertEquals("vi", poi.getSourceLanguageCode());
        assertEquals("DRAFT", poi.getStatus());
        MockMvcBuilders.standaloneSetup(new Probe()).build()
                .perform(post("/poi-probe").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsExactDecimalCoordinateStringsWithoutDoubleConversion() throws Exception {
        String json = """
                {"name":"Precision test","latitude":"10.76261234567890123","longitude":"106.66021234567890123",
                 "radiusM":50,"descriptionSource":"Precision test.","sourceLanguageCode":"vi","status":"DRAFT"}
                """;
        Poi poi = new ObjectMapper().readValue(json, Poi.class);
        assertEquals("10.76261234567890123", poi.getLatitude().toPlainString());
        assertEquals("106.66021234567890123", poi.getLongitude().toPlainString());
        String responseJson = new ObjectMapper().writeValueAsString(poi);
        org.junit.jupiter.api.Assertions.assertTrue(responseJson.contains("\"latitude\":\"10.76261234567890123\""));
        org.junit.jupiter.api.Assertions.assertTrue(responseJson.contains("\"longitude\":\"106.66021234567890123\""));
    }

    @RestController
    static class Probe {
        @PostMapping("/poi-probe")
        String accept(@RequestBody Poi poi) { return poi.getName(); }
    }
}
