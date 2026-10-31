package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "systemlog_id")
    private Long id;

    @Column(nullable = false)
    private String component; // pipeline | database | auth | api

    @Column(nullable = false)
    private String severity; // INFO | WARNING | ERROR | CRITICAL

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "occurred_at")
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}