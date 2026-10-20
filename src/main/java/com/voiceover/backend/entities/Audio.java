package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audio")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Audio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audio_id")
    private Long id;

    @Column(name = "translation_id", nullable = false)
    private Long translationId;

    @Column(name = "poi_id", nullable = false)
    private Long poiId;

    @Column(name = "language_code", length = 10, nullable = false)
    private String languageCode;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "durtation_rec")
    private Integer durationSec;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING | PROCESSING | COMPLETED | FAILED | TEXT_ONLY

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}