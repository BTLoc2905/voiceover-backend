package com.voiceover.backend.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "application", "VoiceMap multilingual auto-tour guide",
                "status", "UP",
                "frontend", "http://localhost:5173",
                "languagesApi", "/api/public/languages",
                "nearbyPoisApi", "/api/public/pois/nearby?lat=10.7770&lng=106.6955"
        );
    }
}
