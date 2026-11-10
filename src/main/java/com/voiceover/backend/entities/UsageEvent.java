package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usage_event")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UsageEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usage_event_id")
    private Long id;

    @Column(name = "anon_client_id")
    private String anonClientId;

    @Column(name = "playback_id", length = 64)
    private String playbackId;

    @Column(name = "event_type", nullable = false)
    private String eventType; // APP_OPEN | AUTO_PLAY | TAP_POI | AUDIO_COMPLETED | ROUTE_SELECTED

    @Column(name = "poi_id")
    private Long poiId;

    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "language_code", length = 10)
    private String languageCode;

    @Column(name = "occurred_at")
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}
