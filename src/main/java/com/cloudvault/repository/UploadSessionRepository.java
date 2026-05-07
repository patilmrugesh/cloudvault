package com.cloudvault.repository;

import com.cloudvault.model.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadSessionRepository extends JpaRepository<UploadSession, String> {

    Optional<UploadSession> findBySessionIdAndOwnerUsername(String sessionId, String ownerUsername);

    List<UploadSession> findAllByExpiresAtBeforeAndStatus(
            Instant cutoff, UploadSession.Status status);
}