package com.cloudvault.repository;

import com.cloudvault.model.ChunkHash;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChunkHashRepository extends JpaRepository<ChunkHash, Long> {
    Optional<ChunkHash> findByChunkHash(String chunkHash);
}