package com.cloudvault.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * One row per chunk of a file.
 * Instead of storing the raw chunk at "file_{id}/chunk_{n}",
 * we look up the ChunkHash and use its canonical minioObjectKey.
 * This is what enables deduplication: two files can point to the
 * same ChunkHash without storing the data twice.
 */
@Entity
@Table(
        name = "file_chunk_map",
        uniqueConstraints = @UniqueConstraint(columnNames = {"file_id", "chunkIndex"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileChunkMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    @Column(nullable = false)
    private int chunkIndex;

    /**
     * SHA-256 hash of the plaintext chunk — foreign key into chunk_hashes.
     * Stored as a plain string so we can look it up without a join when needed.
     */
    @Column(nullable = false, length = 64)
    private String chunkHash;
}