package com.cloudvault.service;

import com.cloudvault.model.ChunkHash;
import com.cloudvault.model.FileChunkMap;
import com.cloudvault.model.FileMetadata;
import com.cloudvault.repository.ChunkHashRepository;
import com.cloudvault.repository.FileChunkMapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Feature 2 – Deduplication System
 *
 * Design:
 * - SHA-256 is computed over the PLAINTEXT chunk bytes before encryption.
 *   This means two users uploading the same file will hit the same hash,
 *   regardless of their individual encryption keys.
 * - The canonical MinIO object is encrypted with a GLOBAL dedup key
 *   (app.dedup-key in application.properties), NOT the per-file key.
 *   This is intentional: we need a single ciphertext to store, but each
 *   user's FileMetadata still has its own encryptionKey for per-user data
 *   isolation at the metadata level.
 *
 * ⚠️  Security note: because we hash plaintext, the hash leaks *whether*
 *     two users have the same file (convergent encryption trade-off).
 *     This is the same approach used by Dropbox and most large-scale
 *     storage systems. For higher sensitivity, consider HMAC-SHA256 with
 *     a user-specific salt — that prevents cross-user correlation at the
 *     cost of losing deduplication across users.
 *
 * Storage layout in MinIO:
 *   dedup/<sha256hex>     ← one object per unique chunk, encrypted with dedup key
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final ChunkHashRepository   chunkHashRepository;
    private final FileChunkMapRepository fileChunkMapRepository;
    private final MinioService           minioService;
    private final EncryptionService      encryptionService;

    @Value("${app.dedup-key}")
    private String dedupEncryptionKey;   // AES-256 key in Base64, set in application.properties

    // ── Write path ───────────────────────────────────────────────────────────

    /**
     * Store a chunk with deduplication.
     *
     * If an identical plaintext chunk already exists (same SHA-256),
     * we skip the MinIO write entirely and just bump the reference count.
     * Otherwise we encrypt with the global dedup key and store it.
     *
     * @param plaintext   the raw (unencrypted) chunk bytes
     * @param file        the FileMetadata entity this chunk belongs to
     * @param chunkIndex  position of this chunk within the file
     */
    @Transactional
    public void storeChunk(byte[] plaintext, FileMetadata file, int chunkIndex) throws Exception {

        String hash = sha256Hex(plaintext);
        log.debug("Chunk {} hash={}", chunkIndex, hash);

        ChunkHash chunkHashRecord = chunkHashRepository.findByChunkHash(hash)
                .orElse(null);

        if (chunkHashRecord != null) {
            // Duplicate detected — reuse the existing stored object
            chunkHashRecord.setReferenceCount(chunkHashRecord.getReferenceCount() + 1);
            chunkHashRepository.save(chunkHashRecord);
            log.info("Dedup hit on chunk {} (hash={}), refCount now {}", chunkIndex, hash,
                    chunkHashRecord.getReferenceCount());

        } else {
            // New unique chunk — encrypt and store
            String minioKey = "dedup/" + hash;
            byte[] encrypted = encryptionService.encrypt(plaintext, dedupEncryptionKey);
            minioService.storeAtKey(encrypted, minioKey);

            chunkHashRecord = ChunkHash.builder()
                    .chunkHash(hash)
                    .minioObjectKey(minioKey)
                    .referenceCount(1)
                    .build();
            chunkHashRepository.save(chunkHashRecord);
            log.info("New chunk stored at {}", minioKey);
        }

        // Always record the mapping so we know which object to fetch for this chunk
        FileChunkMap mapping = FileChunkMap.builder()
                .file(file)
                .chunkIndex(chunkIndex)
                .chunkHash(hash)
                .build();
        fileChunkMapRepository.save(mapping);
    }

    // ── Read path ────────────────────────────────────────────────────────────

    /**
     * Retrieve and decrypt a chunk for the given file and index.
     *
     * @return plaintext chunk bytes
     */
    public byte[] retrieveChunk(FileMetadata file, int chunkIndex) throws Exception {

        // Find which hash/object stores this chunk
        List<FileChunkMap> mappings = fileChunkMapRepository.findByFileOrderByChunkIndexAsc(file);

        FileChunkMap mapping = mappings.stream()
                .filter(m -> m.getChunkIndex() == chunkIndex)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "No chunk mapping found for fileId=" + file.getId() + " index=" + chunkIndex));

        ChunkHash chunkHashRecord = chunkHashRepository.findByChunkHash(mapping.getChunkHash())
                .orElseThrow(() -> new RuntimeException(
                        "ChunkHash record missing for hash=" + mapping.getChunkHash()));

        byte[] encrypted = minioService.getAtKey(chunkHashRecord.getMinioObjectKey());
        return encryptionService.decrypt(encrypted, dedupEncryptionKey);
    }

    // ── Delete path ───────────────────────────────────────────────────────────

    /**
     * Remove all chunk references for a deleted file.
     * If a chunk's reference count drops to zero, delete its MinIO object too.
     */
    @Transactional
    public void deleteChunksForFile(FileMetadata file) throws Exception {

        List<FileChunkMap> mappings = fileChunkMapRepository.findByFileOrderByChunkIndexAsc(file);

        for (FileChunkMap mapping : mappings) {
            chunkHashRepository.findByChunkHash(mapping.getChunkHash()).ifPresent(record -> {
                int newCount = record.getReferenceCount() - 1;
                if (newCount <= 0) {
                    try {
                        minioService.deleteAtKey(record.getMinioObjectKey());
                    } catch (Exception e) {
                        log.error("Failed to delete MinIO object {}", record.getMinioObjectKey(), e);
                    }
                    chunkHashRepository.delete(record);
                    log.info("Deleted orphan chunk {}", record.getMinioObjectKey());
                } else {
                    record.setReferenceCount(newCount);
                    chunkHashRepository.save(record);
                    log.info("Decremented refCount for hash={}, now {}", record.getChunkHash(), newCount);
                }
            });
        }

        fileChunkMapRepository.deleteByFile(file);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return HexFormat.of().formatHex(hash);
    }
}