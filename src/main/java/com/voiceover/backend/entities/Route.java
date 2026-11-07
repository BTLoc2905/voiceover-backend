package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "route")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT"; // DRAFT | PUBLISHED | CLOSED
}