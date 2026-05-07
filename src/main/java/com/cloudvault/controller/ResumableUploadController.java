package com.cloudvault.controller;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.model.UploadSession;
import com.cloudvault.service.ResumableUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Map;

/**
 * Feature 3 – Resumable Upload API
 *
 * Client protocol:
 *
 *   Step 1 – Init:
 *     POST /api/files/upload/init
 *     Body: { "filename": "video.mp4", "contentType": "video/mp4", "totalSize": 104857600 }
 *     Response: { "sessionId": "abc123", "totalChunks": 50, "chunkSize": 2097152 }
 *
 *   Step 2 – Upload chunks (repeat for each chunk):
 *     PUT /api/files/upload/chunk?sessionId=abc123&chunkIndex=0
 *     Body: multipart/form-data with "chunk" field
 *     Response: { "uploaded": 1, "total": 50 }
 *
 *   Step 3 – Check status (after reconnect):
 *     GET /api/files/upload/{sessionId}/status
 *     Response: { "sessionId": "abc123", "totalChunks": 50, "uploadedChunks": [0,1,3,...], ... }
 *
 *   Step 4 – Commit:
 *     POST /api/files/upload/{sessionId}/commit
 *     Response: { "fileId": 42, "filename": "video.mp4" }
 */
@Slf4j
@RestController
@RequestMapping("/api/files/upload")
@RequiredArgsConstructor
public class ResumableUploadController {

    private static final int CHUNK_SIZE_BYTES = 2 * 1024 * 1024;

    private final ResumableUploadService resumableUploadService;

    // ── Step 1: Init ──────────────────────────────────────────────────────────

    @PostMapping("/init")
    public ResponseEntity<?> initSession(
            @RequestBody Map<String, Object> body,
            Principal principal) {

        try {
            String filename    = (String) body.get("filename");
            String contentType = (String) body.getOrDefault("contentType", "application/octet-stream");
            long   totalSize   = Long.parseLong(body.get("totalSize").toString());

            if (filename == null || filename.isBlank()) {
                return ResponseEntity.badRequest().body("filename is required");
            }
            if (totalSize <= 0) {
                return ResponseEntity.badRequest().body("totalSize must be > 0");
            }

            UploadSession session = resumableUploadService.initSession(
                    filename, contentType, totalSize, principal.getName());

            return ResponseEntity.ok(Map.of(
                    "sessionId",   session.getSessionId(),
                    "totalChunks", session.getTotalChunks(),
                    "chunkSize",   CHUNK_SIZE_BYTES
            ));

        } catch (Exception e) {
            log.error("Failed to init upload session", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ── Step 2: Upload a chunk ────────────────────────────────────────────────

    /**
     * Idempotent: safe to retry. If the chunk was already received,
     * the response is still 200 OK.
     */
    @PutMapping("/chunk")
    public ResponseEntity<?> uploadChunk(
            @RequestParam String  sessionId,
            @RequestParam int     chunkIndex,
            @RequestParam("chunk") MultipartFile chunk,
            Principal principal) {

        try {
            if (chunk == null || chunk.isEmpty()) {
                return ResponseEntity.badRequest().body("chunk data is required");
            }

            resumableUploadService.uploadChunk(
                    sessionId, chunkIndex, chunk.getBytes(), principal.getName());

            ResumableUploadService.UploadSessionStatus status =
                    resumableUploadService.getStatus(sessionId, principal.getName());

            return ResponseEntity.ok(Map.of(
                    "uploaded",   status.uploadedChunks().size(),
                    "total",      status.totalChunks(),
                    "chunkIndex", chunkIndex
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Chunk upload failed sessionId={} index={}", sessionId, chunkIndex, e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ── Step 3: Status (resume support) ──────────────────────────────────────

    @GetMapping("/{sessionId}/status")
    public ResponseEntity<?> getStatus(
            @PathVariable String sessionId,
            Principal principal) {

        try {
            ResumableUploadService.UploadSessionStatus status =
                    resumableUploadService.getStatus(sessionId, principal.getName());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Status check failed sessionId={}", sessionId, e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ── Step 4: Commit ────────────────────────────────────────────────────────

    @PostMapping("/{sessionId}/commit")
    public ResponseEntity<?> commit(
            @PathVariable String sessionId,
            Principal principal) {

        try {
            FileMetadata file = resumableUploadService.commitSession(sessionId, principal.getName());
            return ResponseEntity.ok(Map.of(
                    "fileId",   file.getId(),
                    "filename", file.getFileName(),
                    "message",  "Upload complete"
            ));
        } catch (IllegalStateException e) {
            // Missing chunks — tell the client exactly what went wrong
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Commit failed sessionId={}", sessionId, e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}