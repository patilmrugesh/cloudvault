package com.cloudvault.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Maps a SHA-256 hash of a plaintext chunk to the single MinIO object
 * that stores it. All files whose chunk has the same hash share
 * the same stored object — deduplication at chunk level.
 */
@Entity
@Table(
        name = "chunk_hashes",
        uniqueConstraints = @UniqueConstraint(columnNames = "chunkHash")
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkHash {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 hex digest of the plaintext chunk bytes */
    @Column(nullable = false, unique = true, length = 64)
    private String chunkHash;

    /**
     * The canonical MinIO object key for this chunk.
     * Format: "dedup/<sha256hex>"
     * Encrypted with the GLOBAL dedup key stored in application.properties.
     */
    @Column(nullable = false, length = 128)
    private String minioObjectKey;

    /** Number of FileMetadata records that reference this chunk — for GC purposes */
    @Builder.Default
    @Column(nullable = false)
    private int referenceCount = 1;
}