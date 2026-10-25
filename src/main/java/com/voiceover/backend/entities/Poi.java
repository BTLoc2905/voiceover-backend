package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "poi")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Poi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "poi_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "latitude", nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false)
    private BigDecimal longitude;

    @Column(name = "radius_m", nullable = false)
    @Builder.Default
    private Integer radiusM = 50; // Mặc định 50m theo UC02 & F-01

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "description_source", columnDefinition = "TEXT")
    private String descriptionSource;

    @Column(name = "source_language_code", length = 10, nullable = false)
    @Builder.Default
    private String sourceLanguageCode = "vi";

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String status = "DRAFT"; // DRAFT | PUBLISHED | HIDDEN

    @Column(name = "update_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}