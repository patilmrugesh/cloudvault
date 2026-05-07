package com.cloudvault.controller;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.service.FileService;
import com.cloudvault.service.ShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Feature 1 – Share Link REST API
 *
 * Endpoints:
 *
 *   POST   /api/share/create                  → generate a signed URL
 *   DELETE /api/share/{token}                 → revoke a share link (owner only)
 *   GET    /api/share/{token}                 → download via share link (public)
 *
 * The download endpoint is intentionally unauthenticated — the opaque token
 * IS the credential. Password (if required) is passed as a query param or
 * JSON body depending on the client's capability.
 */
@Slf4j
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final FileService  fileService;

    // ── Create a share link ────────────────────────────────────────────────────

    /**
     * Request body:
     * {
     *   "fileId":      123,
     *   "ttlMinutes":  10,        // optional, defaults to 10
     *   "password":    "s3cr3t"   // optional
     * }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createShareLink(
            @RequestBody Map<String, Object> body,
            Principal principal) {

        try {
            Long   fileId     = Long.valueOf(body.get("fileId").toString());
            long   ttl        = body.containsKey("ttlMinutes")
                    ? Long.parseLong(body.get("ttlMinutes").toString())
                    : 10L;
            String password   = (String) body.getOrDefault("password", null);

            String url = shareService.createShareLink(fileId, principal.getName(), ttl, password);
            return ResponseEntity.ok(Map.of("shareUrl", url, "ttlMinutes", ttl));

        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create share link", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ── Revoke a share link ───────────────────────────────────────────────────

    @DeleteMapping("/{token}")
    public ResponseEntity<?> revokeShareLink(
            @PathVariable String token,
            Principal principal) {

        try {
            shareService.revokeToken(token, principal.getName());
            return ResponseEntity.ok("Share link revoked");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to revoke share link", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    // ── Download via share link (public endpoint, no auth required) ───────────

    /**
     * Optional password via query param: GET /api/share/{token}?password=s3cr3t
     *
     * Why query param and not a header?
     * - Easier to embed in HTML href links (e.g. email sharing)
     * - HTTPS encrypts query params in transit — safe to use here
     * - For highly sensitive cases, upgrade to POST with a JSON body
     */
    @GetMapping("/{token}")
    public ResponseEntity<?> downloadViaShareLink(
            @PathVariable String token,
            @RequestParam(required = false) String password) {

        try {
            // Validates token, expiry, revocation, and password
            FileMetadata file = shareService.resolveToken(token, password);

            byte[] data = fileService.downloadFile(file.getId(),
                    file.getOwner().getUsername());

            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(file.getContentType());
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getFileName() + "\"")
                    .contentLength(data.length)
                    .body(data);

        } catch (SecurityException e) {
            // Expired / revoked / wrong password
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            log.error("Share download failed for token={}", token, e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}