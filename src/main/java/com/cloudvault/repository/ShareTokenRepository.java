package com.cloudvault.repository;

import com.cloudvault.model.ShareToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShareTokenRepository extends JpaRepository<ShareToken, Long> {

    Optional<ShareToken> findByToken(String token);

    /** Used by the cleanup scheduler to delete expired tokens */
    List<ShareToken> findAllByExpiresAtBefore(Instant cutoff);
}