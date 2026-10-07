package com.cloudvault.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiIndexStatus aiIndexStatus = AiIndexStatus.NOT_INDEXED;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    private String contentType;

    private Long fileSize;

    private Integer totalChunks;

    // Hidden from JSON responses — but still readable internally via getter
    @JsonIgnore
    @Column(nullable = false)
    private String encryptionKey;

    private LocalDateTime uploadDate;

    // FIX: EAGER so owner.getUsername() never triggers LazyInitializationException
    // outside a transaction (e.g. inside CompletableFuture threads).
    // @JsonIgnore prevents the full User object being serialised into API responses.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User owner;

    // Expose only the username string in API responses — not the full User entity
    @Transient
    public String getOwnerUsername() {
        return owner != null ? owner.getUsername() : null;
    }

    @PrePersist
    protected void onCreate() {
        uploadDate = LocalDateTime.now();
    }
}