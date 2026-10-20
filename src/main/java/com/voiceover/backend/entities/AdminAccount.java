package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "admin_account")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_account_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE | LOCKED

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "ADMIN"; // ADMIN | STAFF
}