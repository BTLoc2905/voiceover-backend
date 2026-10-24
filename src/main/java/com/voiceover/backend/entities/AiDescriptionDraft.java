package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_description_draft", indexes = {
        @Index(name = "idx_ai_description_draft_poi_created", columnList = "poi_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AiDescriptionDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "draft_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poi_id", nullable = false)
    private Poi poi;

    @Column(name = "source_snapshot", nullable = false, columnDefinition = "TEXT")
    private String sourceSnapshot;

    @Column(name = "draft_content", columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 40)
    private String provider = "gemini";

    @Column(nullable = false, length = 80)
    private String model;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = LocalDateTime.now(); }
}
