package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "protected_term")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProtectedTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "protectedterm_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String term;

    private String note;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}