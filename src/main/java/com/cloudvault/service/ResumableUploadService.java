package com.cloudvault.service;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.model.UploadSession;
import com.cloudvault.model.User;
import com.cloudvault.repository.FileRepository;
import com.cloudvault.repository.UploadSessionRepository;
import com.cloudvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Feature 3 – Resumable Uploads
 *
 * Protocol:
 *   1. POST   /api/files/upload/init           → returns sessionId + totalChunks
 *   2. PUT    /api/files/upload/chunk           → upload one chunk by index
 *   3. GET    /api/files/upload/{id}/status     → returns uploaded chunk indices (for resume)
 *   4. POST   /api/files/upload/{id}/commit     → finalise when all chunks present
 *
 * Each chunk is stored via DeduplicationService, so resumable uploads and
 * deduplication work together automatically.
 *
 * Sessions expire after 48 hours. A scheduled job marks them EXPIRED and
 * orphaned MinIO chunks are left for the dedup GC (reference counts keep them safe).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumableUploadService {

    private static final int    CHUNK_SIZE_BYTES  = 2 * 1024 * 1024; // 2 MB
    private static final long   SESSION_TTL_HOURS = 48;

    private final UploadSessionRepository uploadSessionRepository;
    private final FileRepository          fileRepository;
    private final UserRepository          userRepository;
    private final EncryptionService       encryptionService;
    private final DeduplicationService    deduplicationService;

    // ── Step 1: Initialise ────────────────────────────────────────────────────

    /**
     * Create a new upload session. The client must supply the total file size
     * so we can compute how many chunks to expect.
     *
     * @param filename    original file name
     * @param contentType MIME type
     * @param totalSize   total file size in bytes
     * @param username    authenticated user
     * @return UploadSession (sessionId + totalChunks exposed to client)
     */
    @Transactional
    public UploadSession initSession(String filename, String contentType,
                                     long totalSize, String username) throws Exception {

        // Validate user exists
        userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        int totalChunks = (int) Math.ceil((double) totalSize / CHUNK_SIZE_BYTES);
        if (totalChunks < 1) totalChunks = 1;

        String sessionId      = UUID.randomUUID().toString().replace("-", "");
        String encryptionKey  = encryptionService.generateKey();
        Instant now           = Instant.now();

        UploadSession session = UploadSession.builder()
                .sessionId(sessionId)
                .filename(filename)
                .contentType(contentType)
                .totalSize(totalSize)
                .totalChunks(totalChunks)
                .ownerUsername(username)
                .encryptionKey(encryptionKey)
                .createdAt(now)
                .expiresAt(now.plus(SESSION_TTL_HOURS, ChronoUnit.HOURS))
                .build();

        uploadSessionRepository.save(session);
        log.info("Upload session {} created for user={}, chunks={}", sessionId, username, totalChunks);
        return session;
    }

    // ── Step 2: Upload a chunk ────────────────────────────────────────────────

    /**
     * Accept a single chunk. Idempotent: if the chunk was already uploaded,
     * the call succeeds silently (safe to retry).
     *
     * @param sessionId  the session this chunk belongs to
     * @param chunkIndex zero-based chunk index
     * @param data       raw plaintext chunk bytes from the client
     * @param username   must match session owner
     */
    @Transactional
    public void uploadChunk(String sessionId, int chunkIndex,
                            byte[] data, String username) throws Exception {

        UploadSession session = getSessionForUser(sessionId, username);

        if (session.getStatus() != UploadSession.Status.IN_PROGRESS) {
            throw new IllegalStateException("Session " + sessionId + " is not in progress (status=" + session.getStatus() + ")");
        }

        if (Instant.now().isAfter(session.getExpiresAt())) {
            session.setStatus(UploadSession.Status.EXPIRED);
            uploadSessionRepository.save(session);
            throw new IllegalStateException("Upload session has expired. Please start a new upload.");
        }

        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new IllegalArgumentException("chunkIndex " + chunkIndex + " out of range [0, " + session.getTotalChunks() + ")");
        }

        // Idempotency: skip already-uploaded chunks (safe to call again after network failure)
        if (session.getUploadedChunkSet().contains(chunkIndex)) {
            log.info("Chunk {} already uploaded for session {}, skipping", chunkIndex, sessionId);
            return;
        }

        // We need a FileMetadata placeholder so DeduplicationService can create FileChunkMap rows.
        // On first chunk, create it; subsequent chunks find it by sessionId convention.
        FileMetadata placeholder = getOrCreatePlaceholder(session);

        deduplicationService.storeChunk(data, placeholder, chunkIndex);

        session.markChunkUploaded(chunkIndex);
        uploadSessionRepository.save(session);

        log.info("Session {} chunk {}/{} uploaded", sessionId, chunkIndex + 1, session.getTotalChunks());
    }

    // ── Step 3: Query status (for resume) ─────────────────────────────────────

    /**
     * Return the set of chunk indices that have already been uploaded.
     * The client uses this after a reconnect to know which chunks to skip.
     */
    @Transactional(readOnly = true)
    public UploadSessionStatus getStatus(String sessionId, String username) {
        UploadSession session = getSessionForUser(sessionId, username);
        return new UploadSessionStatus(
                session.getSessionId(),
                session.getTotalChunks(),
                session.getUploadedChunkSet(),
                session.getStatus().name(),
                session.getExpiresAt()
        );
    }

    // ── Step 4: Commit ────────────────────────────────────────────────────────

    /**
     * Finalise the upload. Verifies all chunks are present, then
     * marks the FileMetadata as complete so it appears in the user's file list.
     *
     * @return the completed FileMetadata
     */
    @Transactional
    public FileMetadata commitSession(String sessionId, String username) {

        UploadSession session = getSessionForUser(sessionId, username);

        if (session.getStatus() == UploadSession.Status.COMMITTED) {
            // Idempotent: return the already-committed file
            return fileRepository.findById(session.getFinalFileId())
                    .orElseThrow(() -> new RuntimeException("Committed file missing from DB"));
        }

        if (!session.isComplete()) {
            Set<Integer> uploaded = session.getUploadedChunkSet();
            int missing = session.getTotalChunks() - uploaded.size();
            throw new IllegalStateException(
                    "Cannot commit: " + missing + " chunk(s) still missing. " +
                            "Call /status to see which indices are needed.");
        }

        // Promote the placeholder to a real, visible file
        FileMetadata file = fileRepository.findById(session.getFinalFileId())
                .orElseThrow(() -> new RuntimeException("Placeholder FileMetadata not found"));

        file.setTotalChunks(session.getTotalChunks());
        FileMetadata saved = fileRepository.save(file);

        session.setStatus(UploadSession.Status.COMMITTED);
        uploadSessionRepository.save(session);

        log.info("Session {} committed → fileId={}", sessionId, saved.getId());
        return saved;
    }

    // ── Scheduled cleanup ─────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 3_600_000)   // every hour
    @Transactional
    public void expireOldSessions() {
        List<UploadSession> stale = uploadSessionRepository
                .findAllByExpiresAtBeforeAndStatus(Instant.now(), UploadSession.Status.IN_PROGRESS);
        for (UploadSession s : stale) {
            s.setStatus(UploadSession.Status.EXPIRED);
        }
        if (!stale.isEmpty()) {
            uploadSessionRepository.saveAll(stale);
            log.info("Expired {} stale upload sessions", stale.size());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private UploadSession getSessionForUser(String sessionId, String username) {
        return uploadSessionRepository
                .findBySessionIdAndOwnerUsername(sessionId, username)
                .orElseThrow(() -> new RuntimeException(
                        "Upload session not found or does not belong to this user"));
    }

    /**
     * On first chunk upload, create a FileMetadata placeholder (no totalChunks yet).
     * Store its ID in the session so subsequent chunks can find it.
     */
    private FileMetadata getOrCreatePlaceholder(UploadSession session) {
        if (session.getFinalFileId() != null) {
            return fileRepository.findById(session.getFinalFileId())
                    .orElseThrow(() -> new RuntimeException("Placeholder FileMetadata missing"));
        }

        User owner = userRepository.findByUsername(session.getOwnerUsername())
                .orElseThrow(() -> new RuntimeException("User not found: " + session.getOwnerUsername()));

        FileMetadata placeholder = FileMetadata.builder()
                .fileName(session.getFilename())
                .contentType(session.getContentType())
                .fileSize(session.getTotalSize())
                .encryptionKey(session.getEncryptionKey())
                .owner(owner)
                // totalChunks left null until commit — signals "upload in progress"
                .build();

        FileMetadata saved = fileRepository.save(placeholder);
        session.setFinalFileId(saved.getId());
        uploadSessionRepository.save(session);
        return saved;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record UploadSessionStatus(
            String sessionId,
            int totalChunks,
            Set<Integer> uploadedChunks,
            String status,
            Instant expiresAt
    ) {}
}