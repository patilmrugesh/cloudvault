package com.cloudvault.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks the state of an in-progress chunked upload so it can be resumed
 * if the client disconnects or a chunk fails mid-way.
 *
 * Lifecycle:
 *   1. POST /api/files/upload/init   -> creates session, returns sessionId
 *   2. PUT  /api/files/upload/chunk  -> uploads one chunk, marks it complete here
 *   3. POST /api/files/upload/commit -> all chunks present, finalise FileMetadata
 *
 * If the client reconnects, GET /api/files/upload/{sessionId}/status
 * returns the set of already-uploaded chunk indices so it can skip them.
 */
@Entity
@Table(name = "upload_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSession {

    @Id
    @Column(length = 64)
    private String sessionId;          // UUID generated at init time

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long totalSize;            // bytes — sent by client at init

    @Column(nullable = false)
    private int totalChunks;           // ceil(totalSize / CHUNK_SIZE)

    @Column(nullable = false)
    private String ownerUsername;

    @Column(nullable = false)
    private String encryptionKey;      // generated at init, same as FileMetadata.encryptionKey

    /**
     * Which chunk indices have been successfully stored in MinIO.
     * Stored as a comma-separated string for simplicity.
     * Example: "0,1,3" means chunks 0, 1, and 3 are done; chunk 2 still missing.
     */
    @Builder.Default
    @Column(nullable = false, length = 4096)
    private String uploadedChunks = "";

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.IN_PROGRESS;

    /** The FileMetadata ID created when the session is committed */
    @Column
    private Long finalFileId;

    @Column(nullable = false)
    private Instant createdAt;

    /** Sessions older than this should be cleaned up by a scheduled job */
    @Column(nullable = false)
    private Instant expiresAt;

    public enum Status {
        IN_PROGRESS,
        COMMITTED,
        EXPIRED
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    public Set<Integer> getUploadedChunkSet() {
        Set<Integer> set = new HashSet<>();
        if (uploadedChunks == null || uploadedChunks.isBlank()) return set;
        for (String s : uploadedChunks.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) set.add(Integer.parseInt(trimmed));
        }
        return set;
    }

    public void markChunkUploaded(int index) {
        Set<Integer> set = getUploadedChunkSet();
        set.add(index);
        this.uploadedChunks = set.stream()
                .sorted()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    public boolean isComplete() {
        return getUploadedChunkSet().size() == totalChunks;
    }
}