package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "translation", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"poi_id", "language_code"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Translation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "translation_id")
    private Long id;

    @Column(name = "poi_id", nullable = false)
    private Long poiId;

    @Column(name = "language_code", length = 10, nullable = false)
    private String languageCode;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_hash")
    private String sourceHash;

    @Column(name = "is_manual_edit")
    private boolean isManualEdit;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING | PROCESSING | COMPLETED | FAILED

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}