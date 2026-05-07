package com.cloudvault.service;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.model.ShareToken;
import com.cloudvault.repository.FileRepository;
import com.cloudvault.repository.ShareTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

/**
 * Feature 1 – File Sharing with Secure Signed URLs
 *
 * Design decisions:
 * - Token is 48 random bytes → 64-char URL-safe Base64 string.
 *   Brute-force probability: 1/2^384 — computationally impossible.
 * - Expiry stored in DB; checked on every access. No JWT needed —
 *   JWTs can't be revoked without a denylist, this approach can.
 * - Optional BCrypt password adds a second factor without extra infrastructure.
 * - Scheduled cleanup purges expired rows so the table stays lean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareTokenRepository shareTokenRepository;
    private final FileRepository       fileRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom          secureRandom    = new SecureRandom();

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ── Token generation ─────────────────────────────────────────────────────

    /**
     * Create a share link for {@code fileId} owned by {@code username}.
     *
     * @param fileId         the file to share
     * @param username       must be the file owner
     * @param ttlMinutes     how long the link stays valid (1–10 080 — one week)
     * @param plainPassword  optional; null means no password required
     * @return the full share URL: {baseUrl}/api/share/{token}
     */
    @Transactional
    public String createShareLink(Long fileId, String username,
                                  long ttlMinutes, String plainPassword) {

        FileMetadata file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));

        if (!file.getOwner().getUsername().equals(username)) {
            throw new SecurityException("You do not own this file");
        }

        if (ttlMinutes < 1 || ttlMinutes > 10_080) {
            throw new IllegalArgumentException("TTL must be between 1 minute and 7 days");
        }

        // 48 random bytes → URL-safe Base64 (no padding), 64 characters
        byte[] raw = new byte[48];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        String passwordHash = (plainPassword != null && !plainPassword.isBlank())
                ? passwordEncoder.encode(plainPassword)
                : null;

        ShareToken shareToken = ShareToken.builder()
                .token(token)
                .file(file)
                .expiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .passwordHash(passwordHash)
                .build();

        shareTokenRepository.save(shareToken);

        String url = baseUrl + "/api/share/" + token;
        log.info("Share link created for fileId={} by user={}, expires in {} min", fileId, username, ttlMinutes);
        return url;
    }

    // ── Token validation + file resolution ───────────────────────────────────

    /**
     * Validate a token and (optionally) a password, then return the file.
     * Throws a descriptive exception on any failure — callers map these to HTTP status codes.
     *
     * @param token         the opaque token from the URL
     * @param plainPassword the password supplied by the requester (may be null)
     * @return the FileMetadata the token grants access to
     */
    @Transactional(readOnly = true)
    public FileMetadata resolveToken(String token, String plainPassword) {

        ShareToken shareToken = shareTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or unknown share token"));

        if (shareToken.isRevoked()) {
            throw new SecurityException("This share link has been revoked");
        }

        if (Instant.now().isAfter(shareToken.getExpiresAt())) {
            throw new SecurityException("This share link has expired");
        }

        // Password check — only performed when a password was set
        if (shareToken.getPasswordHash() != null) {
            if (plainPassword == null || plainPassword.isBlank()) {
                throw new SecurityException("This share link requires a password");
            }
            if (!passwordEncoder.matches(plainPassword, shareToken.getPasswordHash())) {
                throw new SecurityException("Incorrect password for this share link");
            }
        }

        return shareToken.getFile();
    }

    // ── Revocation ────────────────────────────────────────────────────────────

    /**
     * Immediately revoke a share link. Only the file owner may do this.
     */
    @Transactional
    public void revokeToken(String token, String username) {
        ShareToken shareToken = shareTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token not found"));

        if (!shareToken.getFile().getOwner().getUsername().equals(username)) {
            throw new SecurityException("You do not own this share link");
        }

        shareToken.setRevoked(true);
        shareTokenRepository.save(shareToken);
        log.info("Share token revoked by user={}", username);
    }

    // ── Scheduled cleanup ────────────────────────────────────────────────────

    /** Delete DB rows for tokens that expired more than 24 hours ago */
    @Scheduled(fixedDelay = 3_600_000)   // every hour
    @Transactional
    public void cleanupExpiredTokens() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<ShareToken> stale = shareTokenRepository.findAllByExpiresAtBefore(cutoff);
        if (!stale.isEmpty()) {
            shareTokenRepository.deleteAll(stale);
            log.info("Cleaned up {} expired share tokens", stale.size());
        }
    }
}