package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "language")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Language {
    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_default")
    private boolean isDefault;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "tts_supported")
    private boolean ttsSupported;
}