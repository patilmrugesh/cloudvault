package com.cloudvault.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "share_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The random opaque token embedded in the share URL */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /** File this token grants access to */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false)
    private FileMetadata file;

    /** When this token stops being valid */
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * Optional BCrypt-hashed password.
     * Null means no password is required.
     */
    @Column
    private String passwordHash;

    /** Whether this token has been manually revoked */
    @Builder.Default
    @Column(nullable = false)
    private boolean revoked = false;
}